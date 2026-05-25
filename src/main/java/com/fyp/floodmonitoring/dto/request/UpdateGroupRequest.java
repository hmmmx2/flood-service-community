package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Partial update for a community group (admin/moderator edit). The slug is
 * immutable after creation, so it is intentionally absent here. All fields
 * are optional — only non-null values are applied.
 */
public record UpdateGroupRequest(
        @Size(max = 200) String name,
        String description,
        @Size(max = 20) String iconColor
) {}
