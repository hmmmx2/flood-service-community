package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateSettingRequest(
        @NotBlank
        @Pattern(regexp = "pushNotifications|smsNotifications|emailNotifications|lowDataMode",
                 message = "Invalid setting key")
        String key,

        @NotNull Boolean enabled
) {}
