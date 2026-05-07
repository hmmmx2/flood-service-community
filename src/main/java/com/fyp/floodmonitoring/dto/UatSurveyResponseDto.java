package com.fyp.floodmonitoring.dto;

import java.time.Instant;
import java.util.Map;

/**
 * UAT survey response shape returned by the admin list / detail endpoints.
 * The {@code answers} map is decoded from the entity's JSON string by the
 * service layer.
 */
public record UatSurveyResponseDto(
        String id,
        String userId,
        String displayName,
        String role,
        String source,
        Short satisfactionScore,
        Short recommendScore,
        String businessFit,
        Map<String, Object> answers,
        Instant submittedAt,
        String appVersion
) {}
