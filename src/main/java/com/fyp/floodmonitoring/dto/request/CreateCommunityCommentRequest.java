package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCommunityCommentRequest(
        @NotBlank String content
) {}
