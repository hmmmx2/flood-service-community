package com.fyp.floodmonitoring.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the Spring ApplicationEvent system to SSE clients.
 *
 * Using @TransactionalEventListener(phase = AFTER_COMMIT) guarantees that
 * SSE events are only broadcast AFTER the database transaction is durably
 * committed.  If the transaction rolls back (e.g. DB constraint violation),
 * clients never receive a phantom update.
 *
 * For horizontal scaling across multiple Spring Boot instances, replace the
 * direct sseService.broadcast() call with a Redis Pub/Sub publish, and have
 * each instance subscribe to the Redis channel and call broadcast() locally.
 * The current implementation is correct and sufficient for a single instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventBroadcaster {

    private final SseService sseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSensorUpdate(SensorUpdateEvent event) {
        sseService.broadcast(event.getNode());
    }
}
