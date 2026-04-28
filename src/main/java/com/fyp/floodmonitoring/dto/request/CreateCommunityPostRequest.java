package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityPostRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank @Size(max = 20000, message = "Content must not exceed 20000 characters") String content,
        String imageUrl,   // nullable — base64 data URL or external URL
        String groupSlug   // nullable — post to a specific group
) {}
