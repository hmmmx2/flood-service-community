package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.UserNotificationDto;
import com.fyp.floodmonitoring.entity.UserNotification;
import com.fyp.floodmonitoring.repository.UserNotificationRepository;
import com.fyp.floodmonitoring.sse.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticated notification endpoints — drives the bell icon + dropdown
 * on the Community / CRM web frontends.
 *
 * <pre>
 * GET    /notifications?page=0&size=20  — paginated history (newest first)
 * GET    /notifications/unread-count    — { count: N }
 * POST   /notifications/{id}/read       — mark a single row read
 * POST   /notifications/read-all        — mark every unread row read
 * GET    /notifications/stream          — SSE stream of new notifications (event: "notification")
 * </pre>
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final UserNotificationRepository repo;
    private final SseService sseService;

    @GetMapping
    public ResponseEntity<Page<UserNotificationDto>> list(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = UUID.fromString(principal.getUsername());
        int safeSize = Math.max(1, Math.min(50, size));
        Page<UserNotification> rows = repo.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(0, page), safeSize));
        return ResponseEntity.ok(rows.map(UserNotificationDto::from));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(Map.of("count", repo.countByUserIdAndReadAtIsNull(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id
    ) {
        UUID userId = UUID.fromString(principal.getUsername());
        repo.markRead(id, userId, Instant.now());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        int n = repo.markAllRead(userId, Instant.now());
        return ResponseEntity.ok(Map.of("updated", n));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        return sseService.subscribeForUser(userId);
    }
}
