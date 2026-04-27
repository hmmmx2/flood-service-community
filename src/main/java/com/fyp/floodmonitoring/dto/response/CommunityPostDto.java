package com.fyp.floodmonitoring.dto.response;

import java.time.Instant;
import java.util.List;

public record CommunityPostDto(
        String id,
        String authorId,
        String authorName,
        String authorAvatar,
        String groupId,      // nullable
        String groupSlug,    // nullable
        String groupName,    // nullable
        String title,
        String content,
        String imageUrl,
        int likesCount,
        int commentsCount,
        boolean likedByMe,
        Instant createdAt,
        Instant updatedAt,
        List<CommunityCommentDto> comments
) {}
