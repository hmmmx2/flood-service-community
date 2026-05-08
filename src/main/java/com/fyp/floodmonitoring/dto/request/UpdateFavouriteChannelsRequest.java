package com.fyp.floodmonitoring.dto.request;

/**
 * Per-node notification channel preferences for a favourited sensor.
 *
 * <p>Any field left as {@code null} keeps its existing value — only the
 * non-null ones are applied. This lets the UI persist a single toggle
 * without round-tripping the whole record.</p>
 */
public record UpdateFavouriteChannelsRequest(
        Boolean emailEnabled,
        Boolean smsEnabled,
        Boolean whatsappEnabled,
        Boolean pushEnabled
) {}
