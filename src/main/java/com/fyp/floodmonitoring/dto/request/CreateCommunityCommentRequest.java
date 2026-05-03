package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCommunityCommentRequest(
        @NotBlank String content,
        /** Reply parent comment id; omit or null for top-level */
        String parentId
) {}
