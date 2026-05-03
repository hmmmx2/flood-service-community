package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.entity.FloodAlert;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends transactional emails via Resend's SMTP relay.
 *
 * Behaviour:
 *   - RESEND_API_KEY is set (production)  → sends a real email through smtp.resend.com
 *   - RESEND_API_KEY is empty (dev/local) → logs the email body to console instead
 *
 * Wiring:
 *   application.yml configures spring.mail.* to point at smtp.resend.com:465.
 *   The SMTP password is the Resend API key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender  mailSender;
    private final UserRepository  userRepository;

    @Value("${app.email.from-address}")
    private String fromAddress;

    @Value("${app.email.resend-api-key}")
    private String resendApiKey;

    /**
     * In development mode, all outgoing emails are redirected to this address.
     * Resend's test domain (onboarding@resend.dev) only permits sending to
     * your own verified email — set DEV_EMAIL_RECIPIENT in .env to your Gmail.
     */
    @Value("${app.email.dev-recipient:}")
    private String devRecipient;

    @Value("${app.environment:development}")
    private String environment;

    /**
     * Sends a password-reset verification code to the user's email address.
     * Runs asynchronously so the /forgot-password endpoint returns immediately.
     *
     * @param toEmail recipient email
     * @param code    6-digit verification code
     */
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

        if (resendApiKey == null || resendApiKey.isBlank()) {
            // No API key at all — log code to console for local dev
            log.info("[Email DEV] To={} Subject='{}' Code={}", toEmail, subject, code);
            return;
        }

        // In development, Resend's onboarding@resend.dev test domain only allows
        // sending to your own verified email. Redirect all emails to DEV_EMAIL_RECIPIENT.
        String actualRecipient = toEmail;
        if ("development".equals(environment) && devRecipient != null && !devRecipient.isBlank()) {
            log.info("[Email DEV] Redirecting email from {} → {} (dev mode)", toEmail, devRecipient);
            actualRecipient = devRecipient;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(actualRecipient);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("[Email] Password reset code sent to {} (intended: {})", actualRecipient, toEmail);
        } catch (MailException e) {
            // Log but don't rethrow — the reset code is still valid, user can retry
            log.error("[Email] Failed to send reset email to {}: {}", actualRecipient, e.getMessage());
        }
    }

    /**
     * Sends a flood alert email to all users who have email_alerts enabled.
     * Uses HTML for severity-appropriate styling.
     * Runs asynchronously — called from FloodAlertFanOutListener.
     */
    @Async
    public void sendFloodAlertToAllSubscribers(FloodAlert alert) {
        List<User> subscribers = userRepository.findUsersWithEmailAlertsEnabled();
        if (subscribers.isEmpty()) {
            log.debug("[Email] No email subscribers for flood alert nodeId={}", alert.getNodeId());
            return;
        }

        double feet = alert.getWaterLevelMeters() * 3.28084;
        String subject = switch (alert.getSeverity()) {
            case WATCH    -> "[FloodWatch] Flood Watch: " + alert.getNodeName();
            case WARNING  -> "[FloodWatch] ⚠ Flood Warning: " + alert.getNodeName();
            case CRITICAL -> "[FloodWatch] 🆘 CRITICAL FLOOD ALERT: " + alert.getNodeName();
        };

        for (User user : subscribers) {
            String recipient = resolveRecipient(user.getEmail());
            try {
                if (resendApiKey == null || resendApiKey.isBlank()) {
                    log.info("[Email DEV] Flood alert to={} subject='{}'", recipient, subject);
                    continue;
                }
                MimeMessage mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
                helper.setFrom(fromAddress);
                helper.setTo(recipient);
                helper.setSubject(subject);
                helper.setText(buildFloodAlertHtml(user, alert, feet), true);
                mailSender.send(mime);
                log.debug("[Email] Flood alert sent to {} (intended: {})", recipient, user.getEmail());
            } catch (Exception e) {
                log.error("[Email] Failed to send flood alert to {}: {}", recipient, e.getMessage());
            }
        }
        log.info("[Email] Flood alert dispatched to {} subscribers nodeId={} severity={}",
                subscribers.size(), alert.getNodeId(), alert.getSeverity());
    }

    private String resolveRecipient(String originalEmail) {
        if ("development".equals(environment) && devRecipient != null && !devRecipient.isBlank()) {
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

    /**
     * Sends a broadcast alert email.
     * Currently used for admin notification only — push notifications handle mobile users.
     */
    @Async
    public void sendBroadcastAlert(String toEmail, String title, String body) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.info("[Email DEV] Broadcast to={} title='{}'", toEmail, title);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("[Flood Alert] " + title);
            message.setText(body + "\n\n— Flood Monitor System");
            mailSender.send(message);
        } catch (MailException e) {
            log.error("[Email] Failed to send broadcast email to {}: {}", toEmail, e.getMessage());
        }
    }
}
