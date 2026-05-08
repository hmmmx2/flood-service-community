package com.fyp.floodmonitoring.service.notifications;

import com.fyp.floodmonitoring.entity.FloodAlert;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.entity.UserSetting;
import com.fyp.floodmonitoring.repository.UserRepository;
import com.fyp.floodmonitoring.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multichannel fan-out for flood alerts. For each user inside the
 * alert's radius (saved-location pins), checks which channels they've
 * opted in to (in_app / sms / whatsapp / email — email is handled
 * separately by EmailService for back-compat) and dispatches in
 * parallel where possible.
 *
 * Channel matrix (per user_settings keys):
 *   inAppAlerts     → InAppProvider (DB row + SSE)
 *   smsAlerts       → SmsProvider   (Twilio)
 *   whatsappAlerts  → WhatsAppProvider (Twilio)
 *   emailAlerts     → handled by EmailService.sendFloodAlertToAllSubscribers
 *
 * The dispatcher does NOT short-circuit on missing Twilio creds — the
 * providers themselves log "MOCK" and no-op, so the architecture is
 * always exercised end-to-end in dev.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final UserRepository        userRepository;
    private final UserSettingRepository userSettingRepository;
    private final InAppProvider         inAppProvider;
    private final SmsProvider           smsProvider;
    private final WhatsAppProvider      whatsAppProvider;

    private static final Set<String> CHANNEL_KEYS =
            Set.of("inAppAlerts", "smsAlerts", "whatsappAlerts", "emailAlerts");

    /**
     * Fan a flood alert out to every user subscribed to in-app / SMS /
     * WhatsApp inside the radius of the alerting sensor. Email is
     * dispatched separately by EmailService — kept that way to avoid
     * disturbing the existing path that's already proven in production.
     */
    @Async
    public void dispatchFloodAlert(FloodAlert alert, double nodeLat, double nodeLng) {
        List<User> recipients = userRepository.findNotificationSubscribersForFloodAt(nodeLat, nodeLng);
        if (recipients.isEmpty()) {
            log.debug("[Dispatch] No notification subscribers in range nodeId={}", alert.getNodeId());
            return;
        }

        NotificationPayload payload = buildPayload(alert);
        int inAppN = 0, smsN = 0, waN = 0;

        for (User user : recipients) {
            Set<String> enabled = enabledChannels(user.getId());

            if (enabled.contains("inAppAlerts")) {
                try {
                    inAppProvider.deliver(user.getId(), payload);
                    inAppN++;
                } catch (Exception e) {
                    log.warn("[Dispatch] InApp failed userId={}: {}", user.getId(), e.getMessage());
                }
            }
            if (enabled.contains("smsAlerts") && smsProvider.isEnabled()) {
                try {
                    smsProvider.deliver(user.getPhone(), payload);
                    smsN++;
                } catch (Exception e) {
                    log.warn("[Dispatch] SMS failed userId={}: {}", user.getId(), e.getMessage());
                }
            }
            if (enabled.contains("whatsappAlerts") && whatsAppProvider.isEnabled()) {
                try {
                    whatsAppProvider.deliver(user.getPhone(), payload);
                    waN++;
                } catch (Exception e) {
                    log.warn("[Dispatch] WhatsApp failed userId={}: {}", user.getId(), e.getMessage());
                }
            }
        }
        log.info("[Dispatch] Flood alert nodeId={} severity={} → inApp={} sms={} whatsapp={}",
                alert.getNodeId(), alert.getSeverity(), inAppN, smsN, waN);
    }

    /** Read all channel flags for a user in one query. */
    private Set<String> enabledChannels(java.util.UUID userId) {
        List<UserSetting> rows = userSettingRepository.findByUserIdOrderByKeyAsc(userId);
        Set<String> enabled = new HashSet<>();
        for (UserSetting s : rows) {
            if (Boolean.TRUE.equals(s.getEnabled()) && CHANNEL_KEYS.contains(s.getKey())) {
                enabled.add(s.getKey());
            }
        }
        return enabled;
    }

    private NotificationPayload buildPayload(FloodAlert alert) {
        double feet = alert.getWaterLevelMeters() * 3.28084;
        String node = alert.getNodeName() != null ? alert.getNodeName() : alert.getNodeId();
        String severityLabel = switch (alert.getSeverity()) {
            case WATCH    -> "Flood Watch";
            case WARNING  -> "Flood Warning";
            case CRITICAL -> "CRITICAL flood alert";
        };
        String tone = switch (alert.getSeverity()) {
            case WATCH    -> "warning";
            case WARNING  -> "warning";
            case CRITICAL -> "critical";
        };
        String kind = "flood.alert." + alert.getSeverity().name().toLowerCase();
        String title = severityLabel + ": " + node;
        String body = String.format(
                "Water level at %s reached %.1f ft (%s). Stay alert and monitor official channels.",
                node, feet, severityLabel.toLowerCase());
        String smsBody = String.format(
                "FloodWatch %s — %s @ %.1f ft. Stay safe.",
                severityLabel, node, feet);
        return new NotificationPayload(kind, tone, title, body, smsBody, "/flood-map");
    }
}
