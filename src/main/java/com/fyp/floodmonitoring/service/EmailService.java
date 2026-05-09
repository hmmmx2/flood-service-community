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

    /**
     * Public origin used to build clickable links in transactional emails.
     * Defaults to the production Vercel URL; override with COMMUNITY_SITE_URL
     * on Railway if the public hostname changes.
     */
    @Value("${app.community.site-url:https://flood-website-community.vercel.app}")
    private String communitySiteUrl;

    @Async
    public void sendPasswordResetCode(String toEmail, String code) {
        String subject = "Your FloodWatch password reset code";
        String html = buildOtpEmailHtml(
                "Reset your password",
                "Use the code below to set a new password for your FloodWatch account. " +
                        "If you didn't request this, you can safely ignore this email.",
                code,
                "Reset code");
        resend.sendHtml(
                senders.headerFor(EmailSenderResolver.PASSWORD_RESET),
                resolveRecipient(toEmail),
                subject,
                html);
    }

    @Async
    public void sendRegistrationCode(String toEmail, String code) {
        String subject = "Verify your FloodWatch account";
        String html = buildOtpEmailHtml(
                "Welcome to FloodWatch",
                "Enter the code below on the verification screen to finish creating your account. " +
                        "If you didn't sign up, you can safely ignore this email.",
                code,
                "Verification code");
        resend.sendHtml(
                senders.headerFor(EmailSenderResolver.REGISTRATION),
                resolveRecipient(toEmail),
                subject,
                html);
    }

    /**
     * Sends a "someone interacted with your content" email — used for both
     * top-level comments on a user's post and replies to their comments.
     * Delivery happens via the Resend HTTP API; falls back gracefully if
     * the recipient address is missing.
     *
     * @param toEmail        recipient
     * @param actorName      who took the action ("Alwin Tay")
     * @param verb           "commented on your post" / "replied to your comment"
     * @param contextSnippet a short quote of the comment or post for context
     * @param postTitle      the post title (one line, used in heading)
     * @param relativePath   deep link inside the community site (e.g. /post/abc#comment-123)
     */
    @Async
    public void sendSocialInteractionEmail(
            String toEmail,
            String actorName,
            String verb,
            String contextSnippet,
            String postTitle,
            String relativePath) {
        if (toEmail == null || toEmail.isBlank()) return;
        String url = absoluteUrl(relativePath);
        String subject = actorName + " " + verb;
        String html = buildSocialInteractionHtml(actorName, verb, contextSnippet, postTitle, url);
        resend.sendHtml(
                senders.headerFor(EmailSenderResolver.BROADCAST),
                resolveRecipient(toEmail),
                subject,
                html);
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

    /**
     * Branded transactional OTP email. Single-column 600px layout, inline
     * styles only (Gmail strips &lt;style&gt; blocks), system-font stack so it
     * renders cleanly in every client.
     */
    private String buildOtpEmailHtml(String heading, String intro, String code, String codeLabel) {
        return String.format("""
                <!doctype html>
                <html>
                  <body style="margin:0;padding:0;background:#f4f6f8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#111827">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 16px">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(15,23,42,0.06),0 8px 24px rgba(15,23,42,0.05)">
                            <tr>
                              <td style="padding:32px 40px 8px 40px">
                                <table role="presentation" cellpadding="0" cellspacing="0">
                                  <tr>
                                    <td style="padding-right:10px;vertical-align:middle">
                                      <div style="width:36px;height:36px;border-radius:10px;background:linear-gradient(135deg,#2563eb,#1d4ed8);display:inline-block;line-height:36px;text-align:center;color:#ffffff;font-weight:700;font-size:18px">FW</div>
                                    </td>
                                    <td style="vertical-align:middle">
                                      <span style="font-size:16px;font-weight:600;letter-spacing:-0.01em;color:#0f172a">FloodWatch</span>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:8px 40px 0 40px">
                                <h1 style="margin:16px 0 8px 0;font-size:22px;line-height:1.3;font-weight:600;color:#0f172a;letter-spacing:-0.01em">%s</h1>
                                <p style="margin:0 0 24px 0;font-size:15px;line-height:1.55;color:#475569">%s</p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:0 40px">
                                <div style="border:1px solid #e2e8f0;border-radius:12px;background:#f8fafc;padding:20px;text-align:center">
                                  <div style="font-size:11px;font-weight:600;letter-spacing:0.12em;text-transform:uppercase;color:#64748b;margin-bottom:8px">%s</div>
                                  <div style="font-family:'SF Mono',Menlo,Consolas,monospace;font-size:34px;font-weight:700;letter-spacing:0.35em;color:#0f172a">%s</div>
                                  <div style="font-size:12px;color:#94a3b8;margin-top:10px">Expires in 10 minutes</div>
                                </div>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:24px 40px 32px 40px">
                                <p style="margin:0;font-size:13px;line-height:1.6;color:#64748b">
                                  Never share this code with anyone. FloodWatch will never ask for it by phone or email.
                                </p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:20px 40px;background:#f8fafc;border-top:1px solid #e2e8f0">
                                <p style="margin:0;font-size:12px;line-height:1.5;color:#94a3b8;text-align:center">
                                  Sent by FloodWatch &middot; Real-time flood monitoring for vulnerable communities
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """,
                escapeHtml(heading),
                escapeHtml(intro),
                escapeHtml(codeLabel),
                escapeHtml(code));
    }

    private String absoluteUrl(String relativePath) {
        String base = communitySiteUrl != null ? communitySiteUrl.replaceAll("/+$", "") : "";
        if (relativePath == null || relativePath.isBlank()) return base;
        return base + (relativePath.startsWith("/") ? relativePath : "/" + relativePath);
    }

    /**
     * Branded HTML for "someone commented / replied" emails. Same visual
     * vocabulary as the OTP template (FW wordmark, soft card, footer) so
     * the inbox stays consistent. Single primary CTA points back into the
     * conversation thread.
     */
    private String buildSocialInteractionHtml(String actorName, String verb,
                                              String snippet, String postTitle, String url) {
        String safeActor = escapeHtml(actorName);
        String safeVerb = escapeHtml(verb);
        String safeSnippet = escapeHtml(snippet == null ? "" : snippet);
        String safeTitle = escapeHtml(postTitle == null ? "" : postTitle);
        return String.format("""
                <!doctype html>
                <html>
                  <body style="margin:0;padding:0;background:#f4f6f8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#111827">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 16px">
                      <tr><td align="center">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(15,23,42,0.06),0 8px 24px rgba(15,23,42,0.05)">
                          <tr><td style="padding:32px 40px 8px 40px">
                            <table role="presentation" cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="padding-right:10px;vertical-align:middle"><div style="width:36px;height:36px;border-radius:10px;background:linear-gradient(135deg,#2563eb,#1d4ed8);display:inline-block;line-height:36px;text-align:center;color:#ffffff;font-weight:700;font-size:18px">FW</div></td>
                                <td style="vertical-align:middle"><span style="font-size:16px;font-weight:600;letter-spacing:-0.01em;color:#0f172a">FloodWatch</span></td>
                              </tr>
                            </table>
                          </td></tr>
                          <tr><td style="padding:8px 40px 0 40px">
                            <h1 style="margin:16px 0 8px 0;font-size:20px;line-height:1.35;font-weight:600;color:#0f172a;letter-spacing:-0.01em">%s %s</h1>
                            <p style="margin:0 0 18px 0;font-size:14px;line-height:1.55;color:#64748b">on &ldquo;%s&rdquo;</p>
                          </td></tr>
                          <tr><td style="padding:0 40px">
                            <blockquote style="margin:0;border-left:3px solid #2563eb;padding:10px 14px;background:#f8fafc;border-radius:6px;color:#0f172a;font-size:14px;line-height:1.55;white-space:pre-wrap">%s</blockquote>
                          </td></tr>
                          <tr><td style="padding:24px 40px 8px 40px">
                            <a href="%s" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;font-weight:600;font-size:14px;padding:10px 18px;border-radius:8px">View conversation</a>
                          </td></tr>
                          <tr><td style="padding:16px 40px 32px 40px">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#94a3b8">You're receiving this because you have email notifications enabled in your FloodWatch settings. Manage in <a href="%s" style="color:#2563eb;text-decoration:none">Notification settings</a>.</p>
                          </td></tr>
                          <tr><td style="padding:20px 40px;background:#f8fafc;border-top:1px solid #e2e8f0">
                            <p style="margin:0;font-size:12px;line-height:1.5;color:#94a3b8;text-align:center">Sent by FloodWatch &middot; Real-time flood monitoring for vulnerable communities</p>
                          </td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """,
                safeActor, safeVerb, safeTitle, safeSnippet, url, absoluteUrl("/settings#notifications"));
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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
