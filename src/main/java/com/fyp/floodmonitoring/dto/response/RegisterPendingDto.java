package com.fyp.floodmonitoring.dto.response;

/**
 * Returned by {@code POST /auth/register} now that the registration flow
 * is gated by an email-verification code. The frontend should redirect
 * the user to the /verify-email page and prompt them to enter the code
 * delivered to their inbox.
 *
 * <p>{@code devCode} is only populated in {@code app.environment=development}
 * (no Resend API key configured) so the local UI can auto-fill the code
 * without checking a real inbox. In production it is always {@code null}.</p>
 */
public record RegisterPendingDto(
        String email,
        String message,
        String devCode
) {}
