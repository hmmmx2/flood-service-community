package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Used by the registration flow to confirm the 6-digit emailed code. */
public record VerifyEmailRequest(
        @NotBlank @Email String email,
        @NotBlank        String code
) {}
