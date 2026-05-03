package com.fyp.floodmonitoring.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.response.FloodAlertDto;
import com.fyp.floodmonitoring.dto.response.SensorNodeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active SSE connections and broadcasts sensor updates to all subscribers.
 *
 * Uses ConcurrentHashMap<id, SseEmitter> for O(1) removal when connections close.
 * A scheduled heartbeat every 15s prevents proxies and load balancers from
 * silently dropping idle connections.
 *
 * For horizontal scaling across multiple JVM instances, replace the direct
 * broadcast call in SseEventBroadcaster with a Redis Pub/Sub publish, and
 * have each instance subscribe to the Redis channel and call broadcast() locally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // ── Subscribe ─────────────────────────────────────────────────────────────

    public SseEmitter subscribe() {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);   // no timeout — heartbeat keeps it alive

        emitters.put(id, emitter);
        emitter.onCompletion(() -> {
            emitters.remove(id);
            log.debug("[SSE] Client disconnected: id={} remaining={}", id, emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(id);
            log.debug("[SSE] Client timed out: id={}", id);
        });
        emitter.onError(e -> {
            emitters.remove(id);
            log.debug("[SSE] Client error: id={} msg={}", id, e.getMessage());
        });

        log.debug("[SSE] Client connected: id={} total={}", id, emitters.size());
        return emitter;
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    public void broadcast(SensorNodeDto node) {
        if (emitters.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            log.error("[SSE] Serialization error for nodeId={}: {}", node.nodeId(), e.getMessage());
            return;
        }

        List<String> deadIds = new ArrayList<>();
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("sensor-update")
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                deadIds.add(id);
            }
        });

        if (!deadIds.isEmpty()) {
            deadIds.forEach(emitters::remove);
            log.debug("[SSE] Removed {} stale emitters", deadIds.size());
        }

        log.debug("[SSE] Broadcast nodeId={} to {} clients", node.nodeId(), emitters.size());
    }

    /**
     * Broadcasts a flood alert to all connected SSE clients.
     * Event name "flood-alert" — mobile community website listens for this
     * to show an in-browser warning banner.
     */
    public void broadcastFloodAlert(FloodAlertDto alert) {
        if (emitters.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(alert);
        } catch (JsonProcessingException e) {
            log.error("[SSE] Serialization error for flood alert nodeId={}: {}", alert.nodeId(), e.getMessage());
            return;
        }

        List<String> deadIds = new ArrayList<>();
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("flood-alert")
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                deadIds.add(id);
            }
        });

        if (!deadIds.isEmpty()) {
            deadIds.forEach(emitters::remove);
        }

        log.info("[SSE] Flood alert broadcast nodeId={} severity={} to {} clients",
                alert.nodeId(), alert.severity(), emitters.size());
    }

    // ── Heartbeat (every 15s) ─────────────────────────────────────────────────

    @Scheduled(fixedRate = 15_000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;
        List<String> deadIds = new ArrayList<>();
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat")
                        .data(System.currentTimeMillis(), MediaType.TEXT_PLAIN));
            } catch (Exception e) {
                deadIds.add(id);
            }
        });
        deadIds.forEach(emitters::remove);
    }

    public int connectedClients() {
        return emitters.size();
    }
}
