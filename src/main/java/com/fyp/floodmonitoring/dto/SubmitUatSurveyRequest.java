package com.fyp.floodmonitoring.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * Submission payload for a UAT survey. The frontend renders a question
 * schema based on the user's role; the answers map's keys correspond to
 * the question IDs in that schema.
 *
 * The two top-level metrics (satisfactionScore, recommendScore) are also
 * present inside the answers map but are pulled out here so the
 * controller can store them as denormalised columns for fast filtering.
 */
public record SubmitUatSurveyRequest(
        @NotBlank @Pattern(regexp = "user|admin|both") String role,
        @NotBlank @Pattern(regexp = "community|crm") String source,
        @Min(1) @Max(5) Short satisfactionScore,
        @Min(0) @Max(10) Short recommendScore,
        @Pattern(regexp = "meets|partial|misses", message = "businessFit must be meets|partial|misses") String businessFit,
        Map<String, Object> answers,
        String appVersion,
        String userAgent
) {}
