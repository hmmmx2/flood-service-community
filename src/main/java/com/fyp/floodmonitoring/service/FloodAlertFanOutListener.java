package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.FloodAlertDto;
import com.fyp.floodmonitoring.entity.FloodAlert;
import com.fyp.floodmonitoring.event.FloodAlertCreatedEvent;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FloodAlertFanOutListener {

    private final PushNotificationService pushNotificationService;
    private final EmailService            emailService;
    private final SseService              sseService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFloodAlertCommitted(FloodAlertCreatedEvent event) {
        FloodAlert alert = event.getAlert();
        log.info("[FanOut] Dispatching flood alert: id={} severity={} nodeId={}",
                alert.getId(), alert.getSeverity(), alert.getNodeId());

        pushNotificationService.notifyFloodThreshold(alert);
        emailService.sendFloodAlertToAllSubscribers(alert);
        sseService.broadcastFloodAlert(FloodAlertDto.from(alert));
    }
}
