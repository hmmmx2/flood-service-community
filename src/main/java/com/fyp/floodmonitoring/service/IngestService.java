package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.IngestRequest;
import com.fyp.floodmonitoring.dto.response.IngestResponse;
import com.fyp.floodmonitoring.dto.response.SensorNodeDto;
import com.fyp.floodmonitoring.entity.Event;
import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.EventRepository;
import com.fyp.floodmonitoring.repository.NodeRepository;
import com.fyp.floodmonitoring.sse.SensorUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles IoT sensor data ingestion — both from HTTP POST /ingest and MQTT.
 *
 * Key behaviours:
 *  1. Rate-limits per nodeId — rejects readings arriving faster than minIntervalMs.
 *  2. Only writes an Event row when the flood level actually changes (prevents write amplification).
 *  3. Always updates node.lastUpdated and resets isDead=false so online nodes are shown correctly.
 *  4. Publishes a SensorUpdateEvent after the @Transactional commit so SSE broadcasts
 *     reflect only durable DB state (via @TransactionalEventListener AFTER_COMMIT).
 *  5. Evicts "sensors", "dashboard", and "analytics" caches on every accepted reading.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private final NodeRepository          nodeRepository;
    private final EventRepository         eventRepository;
    private final PushNotificationService pushNotificationService;
    private final SensorService           sensorService;
    private final ApplicationEventPublisher eventPublisher;

    /** In-memory rate limiter: nodeId → last accepted timestamp (ms). Single-instance only. */
    private final ConcurrentHashMap<String, Long> lastAcceptedMs = new ConcurrentHashMap<>();

    @Value("${app.mqtt.min-interval-ms:500}")
    private long minIntervalMs;

    // ── Public ingest (HTTP + MQTT path) ─────────────────────────────────────

    @Transactional
    @CacheEvict(value = {"sensors", "analytics", "dashboard"}, allEntries = true)
    public IngestResponse ingest(IngestRequest req) {

        // Rate limiting — discard if same node published too recently
        if (isRateLimited(req.nodeId())) {
            log.trace("[Ingest] Rate-limited: nodeId={}", req.nodeId());
            return new IngestResponse(false, req.nodeId(), false);
        }

        Node node = nodeRepository.findByNodeId(req.nodeId())
                .orElseThrow(() -> AppException.notFound("Node not found: " + req.nodeId()));

        int previousLevel = node.getCurrentLevel() != null ? node.getCurrentLevel() : 0;
        int newLevel      = req.level();
        boolean levelChanged = newLevel != previousLevel;
        boolean levelRaised  = newLevel > previousLevel;

        // Only write an Event row when the level actually changes — prevents 111 inserts/sec
        if (levelChanged) {
            Event event = Event.builder()
                    .nodeId(req.nodeId())
                    .eventType(newLevel >= 2 ? "ALERT" : "UPDATE")
                    .newLevel(newLevel)
                    .createdAt(req.timestamp() != null ? req.timestamp() : Instant.now())
                    .build();
            eventRepository.save(event);
        }

        // Always update the live state on the node row
        node.setCurrentLevel(newLevel);
        node.setLastUpdated(Instant.now());
        node.setIsDead(false);   // node is publishing — it is online
        nodeRepository.save(node);

        // Push notification only when level rises to Warning or Critical
        boolean alertFired = false;
        if (levelRaised && newLevel >= 2) {
            String area = node.getArea() != null ? node.getArea() : "Kuching";
            pushNotificationService.notifyLevelChange(req.nodeId(), newLevel, area);
            alertFired = true;
            log.info("[Ingest] Alert fired: nodeId={} level={}->{}", req.nodeId(), previousLevel, newLevel);
        }

        // Publish after @Transactional commit — SseEventBroadcaster listens with AFTER_COMMIT
        SensorNodeDto dto = sensorService.toDto(node);
        eventPublisher.publishEvent(new SensorUpdateEvent(this, dto));

        log.debug("[Ingest] nodeId={} level={}->{} changed={} alertFired={}",
                req.nodeId(), previousLevel, newLevel, levelChanged, alertFired);
        return new IngestResponse(true, req.nodeId(), alertFired);
    }

    /**
     * Called by MqttIngestListener when a device's LWT (Last Will Testament) fires.
     * Marks the node dead and broadcasts the offline state via SSE.
     */
    @Transactional
    @CacheEvict(value = {"sensors", "analytics", "dashboard"}, allEntries = true)
    public void markOffline(String nodeId) {
        nodeRepository.findByNodeId(nodeId).ifPresentOrElse(node -> {
            if (Boolean.TRUE.equals(node.getIsDead())) return; // already offline, no-op
            node.setIsDead(true);
            node.setLastUpdated(Instant.now());
            nodeRepository.save(node);

            SensorNodeDto dto = sensorService.toDto(node);
            eventPublisher.publishEvent(new SensorUpdateEvent(this, dto));
            log.info("[Ingest] Node offline: nodeId={}", nodeId);
        }, () -> log.warn("[Ingest] markOffline: unknown nodeId={}", nodeId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isRateLimited(String nodeId) {
        long now = System.currentTimeMillis();
        Long last = lastAcceptedMs.get(nodeId);
        if (last != null && (now - last) < minIntervalMs) {
            return true;
        }
        lastAcceptedMs.put(nodeId, now);
        return false;
    }
}
