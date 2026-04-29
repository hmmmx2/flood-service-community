package com.fyp.floodmonitoring.sse;

import com.fyp.floodmonitoring.dto.response.SensorNodeDto;
import org.springframework.context.ApplicationEvent;

/**
 * Published by IngestService after a successful DB commit.
 * SseEventBroadcaster listens with @TransactionalEventListener(AFTER_COMMIT)
 * so SSE clients only receive updates that are durably persisted.
 */
public class SensorUpdateEvent extends ApplicationEvent {

    private final SensorNodeDto node;

    public SensorUpdateEvent(Object source, SensorNodeDto node) {
        super(source);
        this.node = node;
    }

    public SensorNodeDto getNode() {
        return node;
    }
}
