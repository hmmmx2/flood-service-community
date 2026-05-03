package com.fyp.floodmonitoring.event;

import com.fyp.floodmonitoring.entity.FloodAlert;
import org.springframework.context.ApplicationEvent;

/**
 * Published by FloodThresholdService after a FloodAlert row is committed.
 * FloodAlertFanOutListener listens with @TransactionalEventListener(AFTER_COMMIT)
 * so push / email / SSE fan-out only fires for durably persisted alerts.
 */
public class FloodAlertCreatedEvent extends ApplicationEvent {

    private final FloodAlert alert;

    public FloodAlertCreatedEvent(Object source, FloodAlert alert) {
        super(source);
        this.alert = alert;
    }

    public FloodAlert getAlert() {
        return alert;
    }
}
