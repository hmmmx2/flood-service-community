package com.fyp.floodmonitoring.dto;

/**
 * Per-user notification preferences. The flags `notifyEmail` /
 * `notifySms` / `notifyWhatsapp` come from `user_settings` rows
 * (legacy keyed-pair storage), `phoneE164` lives on the user row.
 */
public record NotificationPrefsDto(
        String  phoneE164,
        boolean notifyEmail,
        boolean notifySms,
        boolean notifyWhatsapp,
        boolean notifyInApp
) {}
