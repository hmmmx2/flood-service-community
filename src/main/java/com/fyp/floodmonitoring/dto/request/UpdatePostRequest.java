package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @Size(max = 500) String title,
        @Size(max = 20000, message = "Content must not exceed 20000 characters") String content,
        String imageUrl,       // new image as base64 or URL; null = no change
        boolean removeImage    // true = explicitly clear imageUrl
) {}
