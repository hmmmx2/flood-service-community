package com.fyp.floodmonitoring.dto.response;

public record AuthSessionDto(
        String accessToken,
        String refreshToken,
        String expiresAt
) {}
