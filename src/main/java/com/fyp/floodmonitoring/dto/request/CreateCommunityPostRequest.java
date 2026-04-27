package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityPostRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank String content,
        String imageUrl,   // nullable — base64 data URL or external URL
        String groupSlug   // nullable — post to a specific group
) {}
