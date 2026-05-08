package com.fyp.floodmonitoring.service.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SMS channel — Twilio. Inactive (logs only) when:
 *   • notifications.sms.enabled=false (default), or
 *   • Twilio creds are missing, or
 *   • the user's phone number is empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsProvider {

    private final TwilioClient twilio;

    @Value("${notifications.sms.enabled:false}")
    private boolean enabled;

    @Value("${notifications.twilio.sms.from:}")
    private String fromNumber;

    public boolean isEnabled() { return enabled; }

    public void deliver(String phoneE164, NotificationPayload payload) {
        if (!enabled) {
            log.debug("[SMS] Skipped — channel disabled (notifications.sms.enabled=false)");
            return;
        }
        if (phoneE164 == null || phoneE164.isBlank()) {
            log.debug("[SMS] Skipped — user has no phone configured");
            return;
        }
        String body = payload.smsBody() != null ? payload.smsBody() : payload.title();
        twilio.send(fromNumber, phoneE164, body);
    }
}
