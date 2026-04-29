package com.fyp.floodmonitoring.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * GET /sse/sensors — long-lived Server-Sent Events stream.
 *
 * No authentication required — sensor readings are public data.
 * Permitted in SecurityConfig via .requestMatchers("/sse/**").permitAll()
 *
 * Client usage (browser / Next.js):
 *   const es = new EventSource(`${API_URL}/sse/sensors`);
 *   es.addEventListener("sensor-update", e => {
 *     const node = JSON.parse(e.data);        // SensorNodeDto
 *     setNodes(prev => prev.map(n => n.nodeId === node.nodeId ? { ...n, ...node } : n));
 *   });
 */
@Slf4j
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @GetMapping(value = "/sensors", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSensors() {
        log.debug("[SSE] New subscriber. Active connections: {}", sseService.connectedClients() + 1);
        return sseService.subscribe();
    }
}
