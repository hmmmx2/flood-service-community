package com.fyp.floodmonitoring.dto.response;

import java.time.Instant;

/**
 * Shape returned to the CRM moderation queue. Includes a snapshot of
 * the reported content so admins don't need an extra round-trip
 * (and so the row stays interpretable even after the original is
 * deleted).
 */
public record ContentReportDto(
        String id,
        String targetType,        // POST | COMMENT
        String targetId,
        String targetSnippet,     // short preview of the reported text
        String targetAuthorId,
        String targetAuthorName,
        String parentPostId,      // for COMMENT — link back to the host post
        String parentPostTitle,

        String reporterId,
        String reporterName,

        String reason,
        String details,
        String status,            // pending | reviewed | actioned | dismissed

        String resolvedById,
        String resolvedByName,
        Instant resolvedAt,

        Instant createdAt
) {}
