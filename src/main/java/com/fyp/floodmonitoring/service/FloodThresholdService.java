package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.entity.FloodAlert;
import com.fyp.floodmonitoring.entity.FloodSeverity;
import com.fyp.floodmonitoring.event.FloodAlertCreatedEvent;
import com.fyp.floodmonitoring.repository.FloodAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Evaluates whether an ingest reading crosses a flood threshold,
 * persists a FloodAlert if it does (with 30-min dedup), and
 * publishes FloodAlertCreatedEvent so the @TransactionalEventListener
 * fan-out fires only after the DB row is committed.
 *
 * Level → severity mapping:
 *   1 (≥ 1.0 m / 3.28 ft) → WATCH    (floodwatch-alerts channel)
 *   2 (≥ 2.5 m / 8.20 ft) → WARNING  (flood_emergency channel)
 *   3 (≥ 4.0 m / 13.1 ft) → CRITICAL (flood_emergency channel + sticky)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FloodThresholdService {

    private final FloodAlertRepository  alertRepo;
    private final ApplicationEventPublisher events;

    @Value("${app.flood.dedup-window-minutes:30}")
    private int dedupWindowMinutes;

    private static final double[] LEVEL_METERS = { 0.0, 1.0, 2.5, 4.0 };

    /**
     * Called from IngestService whenever a node's level rises to >= 1.
     * Participates in the caller's @Transactional — the event fires AFTER_COMMIT.
     *
     * @param nodeId   IoT node identifier
     * @param nodeName human-readable node name
     * @param level    1=WATCH, 2=WARNING, 3=CRITICAL
     * @param area     node's area/zone label (Node.area)
     */
    @Transactional
    public void evaluate(String nodeId, String nodeName, int level, String area) {
        FloodSeverity severity = toSeverity(level);

        LocalDateTime dedupCutoff = LocalDateTime.now().minusMinutes(dedupWindowMinutes);
        if (alertRepo.countRecentAlerts(nodeId, severity, dedupCutoff) > 0) {
            log.debug("[Threshold] Dedup suppressed: nodeId={} severity={} within {} min",
                    nodeId, severity, dedupWindowMinutes);
            return;
        }

        FloodAlert alert = FloodAlert.builder()
                .nodeId(nodeId)
                .nodeName(nodeName)
                .waterLevelMeters(LEVEL_METERS[Math.min(level, 3)])
                .severity(severity)
                .zone(area)
                .build();
        FloodAlert saved = alertRepo.save(alert);

        events.publishEvent(new FloodAlertCreatedEvent(this, saved));
        log.info("[Threshold] Alert persisted: id={} nodeId={} severity={}", saved.getId(), nodeId, severity);
    }

    private FloodSeverity toSeverity(int level) {
        return switch (level) {
            case 1  -> FloodSeverity.WATCH;
            case 2  -> FloodSeverity.WARNING;
            default -> FloodSeverity.CRITICAL;
        };
    }
}
