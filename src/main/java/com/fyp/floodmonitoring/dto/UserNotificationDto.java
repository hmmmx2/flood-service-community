package com.fyp.floodmonitoring.dto;

import com.fyp.floodmonitoring.entity.UserNotification;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire-format for the bell dropdown. Excludes user_id (implicit from the
 * authenticated session) and the foreign-key plumbing.
 */
public record UserNotificationDto(
        UUID id,
        String kind,
        String title,
        String body,
        String link,
        String severity,
        Instant readAt,
        Instant createdAt
) {
    public static UserNotificationDto from(UserNotification n) {
        return new UserNotificationDto(
                n.getId(),
                n.getKind(),
                n.getTitle(),
                n.getBody(),
                n.getLink(),
                n.getSeverity(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}
