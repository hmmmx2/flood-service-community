package com.fyp.floodmonitoring.dto.response;

import java.time.Instant;

public record AdminCommentListItemDto(
        String id,
        String postId,
        String postTitle,
        String parentId,
        String authorId,
        String authorName,
        String content,
        int score,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {}
