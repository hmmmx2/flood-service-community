package com.fyp.floodmonitoring.dto.response;

/** Safety awareness content for one section, returned by GET /safety. */
public record SafetyContentDto(
        String section,
        String content,
        String updatedAt
) {}
