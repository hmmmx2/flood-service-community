package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.CreateBroadcastRequest;
import com.fyp.floodmonitoring.dto.response.BroadcastDto;
import com.fyp.floodmonitoring.entity.Broadcast;
import com.fyp.floodmonitoring.repository.BroadcastRepository;
import com.fyp.floodmonitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages emergency broadcasts sent by admins (SCRUM-104).
 * On send: persists the broadcast row and fires push notifications asynchronously.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final BroadcastRepository     broadcastRepository;
    private final UserRepository          userRepository;
    private final PushNotificationService pushNotificationService;

    @Transactional(readOnly = true)
    public List<BroadcastDto> getAll() {
        return broadcastRepository.findAllByOrderBySentAtDesc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public BroadcastDto create(UUID adminId, CreateBroadcastRequest req) {
        // Count all users with push tokens for recipientCount
        long tokenCount = userRepository.findAll()
                .stream()
                .filter(u -> u.getPushToken() != null && !u.getPushToken().isBlank())
                .count();

        Broadcast broadcast = Broadcast.builder()
                .title(req.title())
                .body(req.body())
                .targetZone(req.targetZone() != null ? req.targetZone() : "all")
                .severity(req.severity() != null ? req.severity() : "warning")
                .sentBy(adminId)
                .sentAt(Instant.now())
                .recipientCount((int) tokenCount)
                .build();

        Broadcast saved = broadcastRepository.save(broadcast);

        // Fire push asynchronously — does not block the HTTP response
        pushNotificationService.notifyBroadcast(req.title(), req.body(), saved.getSeverity());
        log.info("[Broadcast] Admin {} sent broadcast '{}' to {} devices",
                adminId, req.title(), tokenCount);

        return toDto(saved);
    }

    private BroadcastDto toDto(Broadcast b) {
        return new BroadcastDto(
                b.getId().toString(),
                b.getTitle(),
                b.getBody(),
                b.getTargetZone(),
                b.getSeverity(),
                b.getSentBy() != null ? b.getSentBy().toString() : null,
                b.getSentAt() != null ? b.getSentAt().toString() : null,
                b.getRecipientCount() != null ? b.getRecipientCount() : 0);
    }
}
