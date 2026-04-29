package com.fyp.floodmonitoring.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.request.IngestRequest;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.service.IngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Processes every MQTT message that arrives on mqttInputChannel.
 *
 * Topic routing:
 *   flood/nodes/{nodeId}/data    → parse JSON payload, call IngestService.ingest()
 *   flood/nodes/{nodeId}/status  → if payload == "offline", call IngestService.markOffline()
 *
 * Security note: nodeId is always extracted from the TOPIC, never from the payload.
 * This prevents a device from spoofing readings for a node it doesn't own, even if
 * it somehow bypasses the Mosquitto ACL.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MqttIngestListener {

    private final IngestService ingestService;
    private final ObjectMapper  objectMapper;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        String topic   = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String payload = message.getPayload().toString().trim();

        if (topic == null) {
            log.warn("[MQTT] Received message with null topic, discarding");
            return;
        }

        try {
            if (topic.endsWith("/data")) {
                handleTelemetry(topic, payload);
            } else if (topic.endsWith("/status")) {
                handleStatus(topic, payload);
            } else {
                log.debug("[MQTT] Unhandled topic: {}", topic);
            }
        } catch (AppException e) {
            // Unknown nodeId — log and discard; don't crash the listener
            log.warn("[MQTT] Rejected message on topic={}: {}", topic, e.getMessage());
        } catch (Exception e) {
            log.error("[MQTT] Failed to process message on topic={} payload={}: {}",
                    topic, payload, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void handleTelemetry(String topic, String payload) throws Exception {
        String nodeId = extractNodeId(topic);  // authoritative — from topic, not payload

        IngestRequest fromPayload = objectMapper.readValue(payload, IngestRequest.class);

        // Rebuild request with nodeId from topic to prevent payload spoofing
        IngestRequest request = new IngestRequest(
                nodeId,
                fromPayload.level(),
                fromPayload.timestamp(),
                fromPayload.waterLevelMeters(),
                fromPayload.temperature(),
                fromPayload.humidity(),
                fromPayload.latitude(),
                fromPayload.longitude()
        );

        ingestService.ingest(request);
        log.debug("[MQTT] Telemetry: nodeId={} level={}", nodeId, fromPayload.level());
    }

    private void handleStatus(String topic, String payload) {
        String nodeId = extractNodeId(topic);
        // Strip surrounding quotes Mosquitto may include in retain payloads
        String status = payload.replace("\"", "").toLowerCase();
        if ("offline".equals(status)) {
            ingestService.markOffline(nodeId);
        }
    }

    /** Extracts nodeId from topic pattern flood/nodes/{nodeId}/data|status */
    private String extractNodeId(String topic) {
        // Parts: [0]=flood [1]=nodes [2]={nodeId} [3]=data|status
        String[] parts = topic.split("/");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Unexpected topic format: " + topic);
        }
        return parts[2];
    }
}
