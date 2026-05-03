package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.entity.FloodAlert;
import com.fyp.floodmonitoring.entity.FloodSeverity;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends push notifications to mobile devices via the Expo Push API.
 *
 * Flow triggered by IngestService when a node level changes:
 *   IoT device  →  POST /ingest  →  IngestService  →  PushNotificationService
 *                                                    →  Expo Push API (https://exp.host/--/api/v2/push/send)
 *                                                    →  FCM / APNs  →  user device
 *
 * Notification preference keys stored in user_settings:
 *   "pushAllWarnings"   — notify on level >= 2  (Warning + Critical)
 *   "pushCriticalOnly"  — notify on level >= 3  (Critical only)
 *   "pushNone"          — no notifications
 *
 * Expo Push API is free for open-source / NGO usage at this scale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final UserRepository userRepository;

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final int EXPO_BATCH_SIZE  = 100;  // Expo recommends max 100 per request

    private final RestClient restClient = RestClient.create();

    /**
     * Sends push notifications to all users who should be notified about a level change.
     * Runs asynchronously so the /ingest endpoint returns immediately.
     *
     * @param nodeId      the node that changed level
     * @param newLevel    0=normal, 1=watch, 2=warning, 3=critical
     * @param area        human-readable area name (e.g. "Kuching")
     */
    @Async
    public void notifyLevelChange(String nodeId, int newLevel, String area) {
        if (newLevel < 2) {
            // Only send push for Warning (2) and Critical (3)
            return;
        }

        String title   = buildTitle(newLevel, area);
        String body    = buildBody(nodeId, newLevel);
        String channel = newLevel >= 3 ? "critical-alerts" : "flood-alerts";

        // Collect tokens from users who want all warnings
        List<String> tokens = new ArrayList<>(
                userRepository.findUsersWithPushTokenAndSetting("pushAllWarnings")
                              .stream()
                              .map(User::getPushToken)
                              .filter(t -> t != null && t.startsWith("ExponentPushToken"))
                              .toList()
        );

        // For Critical alerts also notify users who only want critical
        if (newLevel >= 3) {
            userRepository.findUsersWithPushTokenAndSetting("pushCriticalOnly")
                          .stream()
                          .map(User::getPushToken)
                          .filter(t -> t != null && t.startsWith("ExponentPushToken"))
                          .forEach(tokens::add);
        }

        if (tokens.isEmpty()) {
            log.debug("[Push] No eligible push tokens for nodeId={} level={}", nodeId, newLevel);
            return;
        }

        // Build Expo message payloads (batched)
        List<Map<String, Object>> messages = tokens.stream()
                .map(token -> buildMessage(token, title, body, nodeId, newLevel, channel))
                .toList();

        sendInBatches(messages);
        log.info("[Push] Sent {} notifications for nodeId={} level={}", messages.size(), nodeId, newLevel);
    }

    /**
     * Sends flood threshold push notifications to eligible users.
     * Payload matches the mobile FloodAlertPayload TypeScript interface exactly,
     * so the mobile FloodAlertBanner activates correctly on receipt.
     *
     * Channel routing:
     *   WATCH    → floodwatch-alerts  (normal priority)
     *   WARNING  → flood_emergency    (bypasses DnD)
     *   CRITICAL → flood_emergency    (bypasses DnD, sticky)
     */
    @Async
    public void notifyFloodThreshold(FloodAlert alert) {
        List<String> tokens = new ArrayList<>(
                userRepository.findUsersWithPushTokenAndSetting("pushAllWarnings")
                        .stream()
                        .map(User::getPushToken)
                        .filter(t -> t != null && t.startsWith("ExponentPushToken"))
                        .toList()
        );

        // CRITICAL also notifies "pushCriticalOnly" subscribers
        if (alert.getSeverity() == FloodSeverity.CRITICAL) {
            userRepository.findUsersWithPushTokenAndSetting("pushCriticalOnly")
                    .stream()
                    .map(User::getPushToken)
                    .filter(t -> t != null && t.startsWith("ExponentPushToken"))
                    .forEach(tokens::add);
        }

        if (tokens.isEmpty()) {
            log.debug("[Push] No eligible tokens for flood threshold nodeId={} severity={}",
                    alert.getNodeId(), alert.getSeverity());
            return;
        }

        double waterLevelFeet = alert.getWaterLevelMeters() * 3.28084;
        boolean isCritical    = alert.getSeverity() == FloodSeverity.CRITICAL;
        boolean isWatch       = alert.getSeverity() == FloodSeverity.WATCH;
        String  channelId     = isWatch ? "floodwatch-alerts" : "flood_emergency";
        String  priority      = isWatch ? "normal" : "high";

        String title = switch (alert.getSeverity()) {
            case WATCH    -> "⚠️ Flood Watch — " + alert.getNodeName();
            case WARNING  -> "🚨 Flood Warning — " + alert.getNodeName();
            case CRITICAL -> "🆘 CRITICAL FLOOD — " + alert.getNodeName();
        };
        String body = String.format("Water level: %.1f ft.%s",
                waterLevelFeet,
                alert.getZone() != null ? " Zone: " + alert.getZone() : "");

        // Build payload matching mobile FloodAlertPayload interface
        Map<String, Object> data = Map.of(
                "type",           "flood_alert",
                "nodeId",         alert.getNodeId(),
                "nodeName",       alert.getNodeName(),
                "waterLevelFeet", waterLevelFeet,
                "severity",       alert.getSeverity().name(),
                "zone",           alert.getZone() != null ? alert.getZone() : "",
                "timestamp",      alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : ""
        );

        List<Map<String, Object>> messages = tokens.stream()
                .distinct()
                .map(token -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("to",        token);
                    msg.put("title",     title);
                    msg.put("body",      body);
                    msg.put("sound",     isCritical ? "flood_alarm.wav" : "default");
                    msg.put("priority",  priority);
                    msg.put("channelId", channelId);
                    msg.put("data",      data);
                    if (isCritical) {
                        msg.put("sticky", true);
                        msg.put("ttl",    86400);  // 24 hours
                    }
                    return msg;
                })
                .toList();

        sendInBatches(messages);
        log.info("[Push] Flood threshold: {} notifications sent nodeId={} severity={}",
                messages.size(), alert.getNodeId(), alert.getSeverity());
    }

    /**
     * Sends a broadcast push notification to ALL users who have a valid push token.
     * Used by BroadcastService when an admin sends an emergency broadcast (SCRUM-104).
     * Runs asynchronously so the POST /broadcasts endpoint returns immediately.
     *
     * @return number of tokens notified
     */
    @Async
    public void notifyBroadcast(String title, String body, String severity) {
        String channel = "critical".equals(severity) ? "critical-alerts" : "flood-alerts";

        List<String> tokens = userRepository.findAll()
                .stream()
                .map(User::getPushToken)
                .filter(t -> t != null && t.startsWith("ExponentPushToken"))
                .distinct()
                .toList();

        if (tokens.isEmpty()) {
            log.debug("[Push] No push tokens registered for broadcast");
            return;
        }

        List<Map<String, Object>> messages = tokens.stream()
                .map(token -> Map.<String, Object>of(
                        "to",        token,
                        "title",     title,
                        "body",      body,
                        "sound",     "default",
                        "priority",  "critical".equals(severity) ? "high" : "normal",
                        "channelId", channel))
                .toList();

        sendInBatches(messages);
        log.info("[Push] Broadcast sent to {} devices: {}", messages.size(), title);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Map<String, Object> buildMessage(
            String token, String title, String body,
            String nodeId, int level, String channelId) {
        return Map.of(
            "to",        token,
            "title",     title,
            "body",      body,
            "sound",     "default",
            "priority",  level >= 3 ? "high" : "normal",
            "channelId", channelId,
            "data",      Map.of("nodeId", nodeId, "level", level)
        );
    }

    private void sendInBatches(List<Map<String, Object>> messages) {
        for (int i = 0; i < messages.size(); i += EXPO_BATCH_SIZE) {
            List<Map<String, Object>> batch = messages.subList(i,
                    Math.min(i + EXPO_BATCH_SIZE, messages.size()));
            try {
                restClient.post()
                        .uri(EXPO_PUSH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(batch)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.error("[Push] Failed to send batch starting at index {}: {}", i, e.getMessage());
            }
        }
    }

    private String buildTitle(int level, String area) {
        return switch (level) {
            case 3 -> "CRITICAL Flood Alert — " + area;
            case 2 -> "Flood Warning — " + area;
            default -> "Flood Update — " + area;
        };
    }

    private String buildBody(String nodeId, int level) {
        double metres = switch (level) {
            case 3 -> 4.0;
            case 2 -> 2.5;
            case 1 -> 1.0;
            default -> 0.0;
        };
        String levelName = switch (level) {
            case 3 -> "Critical";
            case 2 -> "Warning";
            case 1 -> "Watch";
            default -> "Normal";
        };
        return String.format("Node %s has reached %s level (%.1fm). Stay alert and follow safety guidelines.",
                nodeId, levelName, metres);
    }
}
