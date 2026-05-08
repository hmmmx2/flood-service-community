package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Resend the email-verification code for a registration that hasn't been confirmed yet. */
public record ResendVerificationRequest(@NotBlank @Email String email) {}
