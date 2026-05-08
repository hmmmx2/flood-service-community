package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin-only update sent from the CRM moderation page when a moderator
 * actions a content report. Allowed transitions:
 *   pending → reviewed | actioned | dismissed
 *   reviewed → actioned | dismissed | pending
 */
public record UpdateContentReportRequest(@NotBlank String status) {}
