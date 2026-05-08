package com.fyp.floodmonitoring.service.notifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin Twilio REST client used by the SMS + WhatsApp providers. We
 * intentionally don't pull in the Twilio Java SDK — it brings ~30 MB
 * of transitive deps for one POST. The Messages endpoint is simple:
 *
 *   POST /2010-04-01/Accounts/{Sid}/Messages.json
 *   Authorization: Basic <Sid:AuthToken>
 *   Content-Type: application/x-www-form-urlencoded
 *   From=...&To=...&Body=...
 *
 * Configuration (application.yml):
 *   notifications.twilio.account-sid
 *   notifications.twilio.auth-token
 *   notifications.twilio.sms.from
 *   notifications.twilio.whatsapp.from   (e.g. "whatsapp:+14155238886" sandbox)
 *
 * When sid + token are missing, send() logs the message at INFO level
 * and returns false — so the rest of the app keeps working in dev /
 * FYP demo mode without real Twilio credentials.
 */
@Slf4j
@Component
public class TwilioClient {

    private final String accountSid;
    private final String authToken;
    private final RestClient http = RestClient.create();

    public TwilioClient(
            @Value("${notifications.twilio.account-sid:}") String accountSid,
            @Value("${notifications.twilio.auth-token:}")  String authToken
    ) {
        this.accountSid = accountSid == null ? "" : accountSid.trim();
        this.authToken  = authToken  == null ? "" : authToken.trim();
    }

    public boolean isConfigured() {
        return !accountSid.isEmpty() && !authToken.isEmpty();
    }

    /**
     * @return true if Twilio accepted the message (HTTP 2xx) or we're
     *         in mock mode (no creds). false on hard failure.
     */
    public boolean send(String from, String to, String body) {
        if (!isConfigured()) {
            log.info("[Twilio MOCK] from={} to={} body=\"{}\" — set notifications.twilio.account-sid + auth-token to deliver",
                    from, to, body);
            return true;
        }
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            log.warn("[Twilio] Skipping send — empty from/to (from={}, to={})", from, to);
            return false;
        }

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
        String basic = Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes());

        // Twilio expects form-encoded bodies, not JSON.
        Map<String, String> form = new LinkedHashMap<>();
        form.put("From", from);
        form.put("To", to);
        form.put("Body", body);
        String formEncoded = UriComponentsBuilder.newInstance()
                .queryParams(toMultiValue(form)).build().getQuery();

        try {
            ResponseEntity<String> res = http.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formEncoded == null ? "" : formEncoded)
                    .retrieve()
                    .toEntity(String.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                log.info("[Twilio] Sent — from={} to={} status={}",
                        from, to, res.getStatusCode().value());
                return true;
            }
            log.warn("[Twilio] Non-2xx — from={} to={} status={} body={}",
                    from, to, res.getStatusCode().value(), res.getBody());
            return false;
        } catch (Exception e) {
            log.error("[Twilio] Send failed — from={} to={}: {}", from, to, e.getMessage());
            return false;
        }
    }

    private static org.springframework.util.MultiValueMap<String, String> toMultiValue(Map<String, String> form) {
        org.springframework.util.LinkedMultiValueMap<String, String> mv =
                new org.springframework.util.LinkedMultiValueMap<>();
        form.forEach(mv::add);
        return mv;
    }
}
