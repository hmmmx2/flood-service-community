package com.fyp.floodmonitoring.service.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * WhatsApp channel — Twilio WhatsApp API. The "from" must be the
 * Twilio sandbox number ("whatsapp:+14155238886") or an approved
 * Business sender; "to" is the user's phone in E.164 prefixed with
 * "whatsapp:". Inactive (logs only) when:
 *   • notifications.whatsapp.enabled=false (default), or
 *   • Twilio creds are missing, or
 *   • the user's phone number is empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppProvider {

    private final TwilioClient twilio;

    @Value("${notifications.whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${notifications.twilio.whatsapp.from:}")
    private String fromNumber;

    public boolean isEnabled() { return enabled; }

    public void deliver(String phoneE164, NotificationPayload payload) {
        if (!enabled) {
            log.debug("[WhatsApp] Skipped — channel disabled (notifications.whatsapp.enabled=false)");
            return;
        }
        if (phoneE164 == null || phoneE164.isBlank()) {
            log.debug("[WhatsApp] Skipped — user has no phone configured");
            return;
        }
        String to = phoneE164.startsWith("whatsapp:") ? phoneE164 : "whatsapp:" + phoneE164;
        String from = (fromNumber == null || fromNumber.isBlank())
                ? ""
                : (fromNumber.startsWith("whatsapp:") ? fromNumber : "whatsapp:" + fromNumber);
        String body = payload.body() != null ? payload.body() : payload.title();
        twilio.send(from, to, body);
    }
}
