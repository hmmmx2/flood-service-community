package com.fyp.floodmonitoring.dto.response;

public record AdminUserDto(
        String id,
        String displayName,
        String email,
        String role,
        String status,
        String createdAt,
        String lastLogin
) {}
