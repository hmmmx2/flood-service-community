package com.fyp.floodmonitoring.service.notifications;

/**
 * Channel-agnostic notification payload. Each provider (InApp / SMS /
 * WhatsApp / Email) projects this onto its own format.
 *
 *   • kind     — machine-readable type (e.g. "flood.alert.warning")
 *   • severity — "info" | "warning" | "critical"
 *   • title    — short headline (≤ 200 chars)
 *   • body     — longer message (≤ 1000 chars)
 *   • smsBody  — compact body for the 160-char SMS budget
 *   • link     — optional deep-link path (e.g. "/flood-map")
 */
public record NotificationPayload(
        String kind,
        String severity,
        String title,
        String body,
        String smsBody,
        String link
) {}
