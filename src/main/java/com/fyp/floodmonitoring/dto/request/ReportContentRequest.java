package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Filed by a community user to flag a post or comment for admin review.
 * The {@code reason} is one of the agreed enum values
 * (spam, harassment, misinformation, off-topic, other); {@code details}
 * is optional free-text and capped to 500 chars to keep storage tight.
 */
public record ReportContentRequest(
        @NotBlank @Size(max = 32) String reason,
        @Size(max = 500) String details
) {}
