package com.fyp.floodmonitoring.service.notifications;

import com.fyp.floodmonitoring.dto.UserNotificationDto;
import com.fyp.floodmonitoring.entity.UserNotification;
import com.fyp.floodmonitoring.repository.UserNotificationRepository;
import com.fyp.floodmonitoring.sse.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Delivers in its OWN transaction (REQUIRES_NEW) so a notification
     * failure can never roll back the caller's transaction. Previously
     * a FK / NOT NULL hiccup here would silently mark the outer
     * @Transactional (e.g. addComment) rollback-only, and the surrounding
     * try/catch couldn't unwind that — so the comment vanished even
     * though the controller returned 200.
     *
     * <p>This method swallows all exceptions: notifications are
     * best-effort and must never break the user-visible action.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UUID userId, NotificationPayload payload) {
        try {
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
        } catch (Exception e) {
            log.warn("[InApp] Delivery failed for userId={} kind={}: {}",
                    userId, payload.kind(), e.getMessage());
        }
    }
}
