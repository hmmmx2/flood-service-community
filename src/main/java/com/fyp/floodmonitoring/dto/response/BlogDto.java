package com.fyp.floodmonitoring.dto.response;

/**
 * Full blog article DTO returned by all blog endpoints.
 */
public record BlogDto(
        String id,
        String imageKey,
        String imageUrl,
        String category,
        String title,
        String body,
        boolean isFeatured,
        String createdAt,
        String updatedAt
) {}
