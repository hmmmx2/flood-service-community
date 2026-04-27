package com.fyp.floodmonitoring.dto.response;

import java.util.List;

/**
 * Generic cursor-paginated result.
 *
 * @param <T> type of items in the page
 */
public record CursorPageDto<T>(
        List<T> data,
        String  nextCursor,   // null when this is the last page
        boolean hasMore
) {}
