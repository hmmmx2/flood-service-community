package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.entity.FloodAlert;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.entity.UserFavouriteNode;
import com.fyp.floodmonitoring.repository.UserFavouriteNodeRepository;
import com.fyp.floodmonitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends transactional emails through Resend's HTTP API. The previous SMTP
 * path via smtp.resend.com:465 silently failed on Railway, so this class
 * now goes through {@link ResendHttpClient} which posts directly to
 * https://api.resend.com/emails — the same path the diagnostic probes used.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final UserRepository              userRepository;
    private final UserFavouriteNodeRepository favRepository;
    private final EmailSenderResolver         senders;
    private final ResendHttpClient            resend;

    @Value("${app.email.dev-recipient:}")
    private String devRecipient;

    @Value("${app.environment:development}")
    private String environment;

    @Async
    public void sendPasswordResetCode(String toEmail, String code) {
        String subject = "Your Flood Monitor password reset code";
        String body = String.format(
                "Hi,%n%n" +
                "You requested a password reset for your Flood Monitor account.%n%n" +
                "Your verification code is:%n%n" +
                "    %s%n%n" +
                "This code expires in 10 minutes.%n%n" +
                "If you did not request this, you can safely ignore this email.%n%n" +
                "— Flood Monitor Team",
                code);

        String recipient = resolveRecipient(toEmail);
        resend.sendText(senders.headerFor(EmailSenderResolver.PASSWORD_RESET), recipient, subject, body);
    }

    @Async
    public void sendRegistrationCode(String toEmail, String code) {
        String subject = "Verify your Flood Monitor account";
        String body = String.format(
                "Welcome to Flood Monitor!%n%n" +
                "To finish creating your account, enter this code on the verification screen:%n%n" +
                "    %s%n%n" +
                "This code expires in 10 minutes. If you didn't sign up, just ignore this email.%n%n" +
                "— Flood Monitor Team",
                code);

        String recipient = resolveRecipient(toEmail);
        resend.sendText(senders.headerFor(EmailSenderResolver.REGISTRATION), recipient, subject, body);
    }

    @Async
    public void sendFloodAlertToAllSubscribers(FloodAlert alert, double nodeLat, double nodeLng) {
        List<User> subscribers = userRepository.findEmailSubscribersForFloodAt(nodeLat, nodeLng);
        if (subscribers.isEmpty()) {
            log.debug("[Email] No email subscribers in range for flood alert nodeId={}", alert.getNodeId());
            return;
        }

        double feet = alert.getWaterLevelMeters() * 3.28084;
        String subject = switch (alert.getSeverity()) {
            case WATCH    -> "[FloodWatch] Flood Alert: " + alert.getNodeName();
            case WARNING  -> "[FloodWatch] ⚠ Flood Warning: " + alert.getNodeName();
            case CRITICAL -> "[FloodWatch] 🆘 CRITICAL FLOOD ALERT: " + alert.getNodeName();
        };
        String fromHeader = senders.headerFor(EmailSenderResolver.FLOOD_ALERT);

        for (User user : subscribers) {
            UserFavouriteNode fav = favRepository
                    .findByUserIdAndBusinessNodeId(user.getId(), alert.getNodeId())
                    .orElse(null);
            if (fav != null && !Boolean.TRUE.equals(fav.getEmailEnabled())) {
                log.debug("[Email] Skipping userId={} — email disabled on favourite for nodeId={}",
                        user.getId(), alert.getNodeId());
                continue;
            }

            String recipient = resolveRecipient(user.getEmail());
            resend.sendHtml(fromHeader, recipient, subject, buildFloodAlertHtml(user, alert, feet));
        }
        log.info("[Email] Flood alert dispatched to {} subscribers nodeId={} severity={}",
                subscribers.size(), alert.getNodeId(), alert.getSeverity());
    }

    @Async
    public void sendBroadcastAlert(String toEmail, String title, String body) {
        resend.sendText(
                senders.headerFor(EmailSenderResolver.BROADCAST),
                resolveRecipient(toEmail),
                "[Flood Alert] " + title,
                body + "\n\n— Flood Monitor System");
    }

    private String resolveRecipient(String originalEmail) {
        if ("development".equals(environment) && devRecipient != null && !devRecipient.isBlank()) {
            log.info("[Email DEV] Redirecting from {} → {} (dev mode)", originalEmail, devRecipient);
            return devRecipient;
        }
        return originalEmail;
    }

    private String buildFloodAlertHtml(User user, FloodAlert alert, double feet) {
        String severityColor = switch (alert.getSeverity()) {
            case WATCH    -> "#d97706";
            case WARNING  -> "#dc2626";
            case CRITICAL -> "#7f1d1d";
        };
        String zoneRow = alert.getZone() != null
                ? "<p>Zone: <strong>" + alert.getZone() + "</strong></p>"
                : "";
        return String.format("""
                <div style="font-family:sans-serif;max-width:600px;margin:0 auto">
                  <div style="background:%s;color:white;padding:16px;border-radius:8px 8px 0 0">
                    <h2 style="margin:0">Flood Alert — %s</h2>
                  </div>
                  <div style="border:2px solid %s;border-top:none;padding:20px;border-radius:0 0 8px 8px">
                    <p>Hello %s,</p>
                    <p>Sensor <strong>%s</strong> has reached <strong>%s</strong> level.</p>
                    <p>Current water level: <strong>%.1f ft (%.1f m)</strong></p>
                    %s
                    <p>Please take appropriate action and monitor official channels.</p>
                    <p><strong>Emergency contacts:</strong> 991 (Police) | 999 (Fire &amp; Rescue) | 994 (Civil Defence)</p>
                    <hr style="margin:16px 0"/>
                    <p style="font-size:12px;color:#666">
                      You received this because you have email alerts enabled in FloodWatch.
                    </p>
                  </div>
                </div>
                """,
                severityColor, alert.getSeverity().name(),
                severityColor,
                user.getFirstName(),
                alert.getNodeName(), alert.getSeverity().name(),
                feet, alert.getWaterLevelMeters(),
                zoneRow);
    }
}
