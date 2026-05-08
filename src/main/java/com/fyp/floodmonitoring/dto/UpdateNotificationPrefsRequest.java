package com.fyp.floodmonitoring.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /profile/notification-prefs body. All fields optional — caller
 * sends only what they're changing. Phone is validated as E.164 to
 * keep the SMS/WhatsApp providers happy.
 */
public record UpdateNotificationPrefsRequest(
        @Size(max = 32)
        @Pattern(regexp = "^\\+?[1-9]\\d{6,15}$|^$",
                 message = "Phone must be in E.164 format, e.g. +60123456789")
        String  phoneE164,
        Boolean notifyEmail,
        Boolean notifySms,
        Boolean notifyWhatsapp,
        Boolean notifyInApp
) {}
