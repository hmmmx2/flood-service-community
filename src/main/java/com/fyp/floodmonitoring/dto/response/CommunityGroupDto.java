package com.fyp.floodmonitoring.dto.response;

import java.time.Instant;

public record CommunityGroupDto(
        String id,
        String slug,
        String name,
        String description,
        String iconLetter,
        String iconColor,
        int membersCount,
        int postsCount,
        boolean joinedByMe,
        Instant createdAt
) {}
