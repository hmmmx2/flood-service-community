package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.FloodAlertDto;
import com.fyp.floodmonitoring.entity.FloodAlert;
import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.event.FloodAlertCreatedEvent;
import com.fyp.floodmonitoring.repository.NodeRepository;
import com.fyp.floodmonitoring.service.notifications.NotificationDispatcher;
import com.fyp.floodmonitoring.sse.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans out a committed FloodAlert to push notifications, email, and SSE —
 * all on a background thread so the ingest response returns immediately.
 *
 * @TransactionalEventListener(AFTER_COMMIT) guarantees the DB row exists
 * before any client receives the alert (no phantom notifications on rollback).
 *
 * @Async moves execution off the ingest request thread so HTTP 200 is
 * returned to the IoT device before push/email I/O completes.
 *
 * Phase 3: email recipients are now filtered by the alerting node's
 * coordinate vs. each user's saved-location pin radii. Push and SSE
 * remain blast-style for now (push is gated by individual user_settings
 * keys; SSE is for the in-page toast which is a UX cue, not a targeted
 * alert). Push will be migrated to radius-aware in a future pass.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FloodAlertFanOutListener {

    private final PushNotificationService pushNotificationService;
    private final EmailService            emailService;
    private final SseService              sseService;
    private final NodeRepository          nodeRepository;
    private final NotificationDispatcher  notificationDispatcher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFloodAlertCommitted(FloodAlertCreatedEvent event) {
        FloodAlert alert = event.getAlert();
        log.info("[FanOut] Dispatching flood alert: id={} severity={} nodeId={}",
                alert.getId(), alert.getSeverity(), alert.getNodeId());

        pushNotificationService.notifyFloodThreshold(alert);
        sseService.broadcastFloodAlert(FloodAlertDto.from(alert));

        // Look up the alerting node's coordinate so the email + multichannel
        // dispatcher can filter recipients by their saved-location pin radii.
        // If we can't find the node row (race / orphaned alert / test data),
        // fall back to broadcasting to everyone — better an extra email than
        // a missed flood warning.
        Node node = nodeRepository.findByNodeId(alert.getNodeId()).orElse(null);
        double lat = (node != null && node.getLatitude()  != null) ? node.getLatitude()  : 0.0;
        double lng = (node != null && node.getLongitude() != null) ? node.getLongitude() : 0.0;
        if (node == null) {
            log.warn("[FanOut] Node not found for nodeId={}, broadcasting to all subscribers", alert.getNodeId());
        }

        emailService.sendFloodAlertToAllSubscribers(alert, lat, lng);
        notificationDispatcher.dispatchFloodAlert(alert, lat, lng);
    }
}
