package com.fyp.floodmonitoring.dto.response;

public record FeaturedBlogDto(
        String id,
        String imageKey,    // "blog-1" | "blog-2"
        String title,
        String text
) {}
