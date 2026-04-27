package com.fyp.floodmonitoring.dto.response;

import java.time.Instant;

public record CommunityCommentDto(
        String id,
        String authorId,
        String authorName,
        String authorAvatar,
        String content,
        Instant createdAt
) {}
