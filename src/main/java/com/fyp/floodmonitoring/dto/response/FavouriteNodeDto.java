package com.fyp.floodmonitoring.dto.response;

import java.util.List;

/**
 * Extends {@link SensorNodeDto} with current flood level, last updated time,
 * and the timestamp when the user bookmarked this node.
 */
public record FavouriteNodeDto(
        String id,
        /** Business key (same as {@link SensorNodeDto#nodeId()}); required by clients for favourites matching. */
        String nodeId,
        String name,
        String status,
        String distance,
        List<Double> coordinate,
        String area,
        String location,
        String state,
        int currentLevel,
        String lastUpdated,
        String favouritedAt
) {}
