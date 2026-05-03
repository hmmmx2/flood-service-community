package com.fyp.floodmonitoring.dto.response;

import java.util.List;

public record CommunityCommentsPageDto(
        List<CommunityCommentDto> comments,
        long totalTopLevel,
        int page,
        int size
) {}
