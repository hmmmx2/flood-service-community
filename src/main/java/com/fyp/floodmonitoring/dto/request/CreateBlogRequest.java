package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBlogRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank String body,
        String imageKey,
        String imageUrl,
        String category,
        Boolean isFeatured
) {}
