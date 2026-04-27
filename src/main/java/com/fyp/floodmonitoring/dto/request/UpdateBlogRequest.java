package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateBlogRequest(
        @Size(max = 500) String title,
        String body,
        String imageKey,
        String imageUrl,
        String category,
        Boolean isFeatured
) {}
