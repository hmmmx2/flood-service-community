package com.fyp.floodmonitoring.dto.response;

import java.util.List;

/**
 * Extends {@link SensorNodeDto} with current flood level, last updated time,
 * the timestamp when the user bookmarked this node, and the per-favourite
 * channel preferences (email / SMS / WhatsApp / in-app push).
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
        String favouritedAt,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean whatsappEnabled,
        boolean pushEnabled
) {}
