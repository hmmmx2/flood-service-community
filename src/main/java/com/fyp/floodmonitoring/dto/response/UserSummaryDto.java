package com.fyp.floodmonitoring.dto.response;

/**
 * User summary returned in login / register responses.
 * Both {@code displayName} (joined) and separate {@code firstName}/{@code lastName}
 * fields are included so web and mobile clients can use whichever they prefer.
 */
public record UserSummaryDto(
        String id,
        String email,
        String firstName,
        String lastName,
        String displayName,   // convenience: firstName + " " + lastName
        String avatarUrl,
        String role
) {}
