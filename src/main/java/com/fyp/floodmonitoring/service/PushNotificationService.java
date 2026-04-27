package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
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
