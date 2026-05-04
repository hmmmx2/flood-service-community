package com.fyp.floodmonitoring.dto.response;

import java.util.List;

public record CommunityCommentsPageDto(
        List<CommunityCommentDto> comments,
        long totalTopLevel,
        int page,
        int size,
        /** All comment rows for this post (roots + replies); matches DB truth used by the listing query. */
        long totalComments
) {}
