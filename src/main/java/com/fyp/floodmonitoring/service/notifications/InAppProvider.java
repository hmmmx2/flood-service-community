package com.fyp.floodmonitoring.service.notifications;

import com.fyp.floodmonitoring.dto.UserNotificationDto;
import com.fyp.floodmonitoring.entity.UserNotification;
import com.fyp.floodmonitoring.repository.UserNotificationRepository;
import com.fyp.floodmonitoring.sse.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * In-app notification channel: persists a row in `user_notifications`
 * and pushes the new row to any active SSE listener for that user
 * (the bell icon dropdown subscribes via /sse/notifications).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppProvider {

    private final UserNotificationRepository repo;
    private final SseService sseService;

    public void deliver(UUID userId, NotificationPayload payload) {
        UserNotification row = UserNotification.builder()
                .userId(userId)
                .kind(payload.kind())
                .title(payload.title())
                .body(payload.body())
                .link(payload.link())
                .severity(payload.severity())
                .build();
        UserNotification saved = repo.save(row);
        sseService.pushNotificationToUser(userId, UserNotificationDto.from(saved));
        log.debug("[InApp] Delivered to userId={} kind={}", userId, payload.kind());
    }
}
