package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.IngestRequest;
import com.fyp.floodmonitoring.dto.response.IngestResponse;
import com.fyp.floodmonitoring.entity.Event;
import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.EventRepository;
import com.fyp.floodmonitoring.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles IoT sensor data ingestion.
 *
 * On each call to ingest():
 *   1. Looks up the node by nodeId — 404 if not found
 *   2. Detects if the flood level has changed
 *   3. Saves a new event row (always)
 *   4. Updates node.currentLevel + node.lastUpdated
 *   5. If level >= 2 and it's a level increase, fires push notifications asynchronously
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private final NodeRepository          nodeRepository;
    private final EventRepository         eventRepository;
    private final PushNotificationService pushNotificationService;

    @Transactional
    @CacheEvict(value = {"analytics", "dashboard"}, allEntries = true)
    public IngestResponse ingest(IngestRequest req) {
        Node node = nodeRepository.findByNodeId(req.nodeId())
                .orElseThrow(() -> AppException.notFound("Node not found: " + req.nodeId()));

        int previousLevel = node.getCurrentLevel() != null ? node.getCurrentLevel() : 0;
        int newLevel      = req.level();
        boolean levelRaised = newLevel > previousLevel;

        // Always record the event regardless of level change
        Event event = Event.builder()
                .nodeId(req.nodeId())
                .eventType(newLevel >= 2 ? "ALERT" : "UPDATE")
                .newLevel(newLevel)
                .createdAt(req.timestamp() != null ? req.timestamp() : Instant.now())
                .build();
        eventRepository.save(event);

        // Update node state
        node.setCurrentLevel(newLevel);
        node.setLastUpdated(Instant.now());
        nodeRepository.save(node);

        // Fire push notifications asynchronously if level rose to Warning or Critical
        boolean alertFired = false;
        if (levelRaised && newLevel >= 2) {
            String area = node.getArea() != null ? node.getArea() : "Kuching";
            pushNotificationService.notifyLevelChange(req.nodeId(), newLevel, area);
            alertFired = true;
            log.info("[Ingest] Alert fired: nodeId={} level={}->{}", req.nodeId(), previousLevel, newLevel);
        }

        log.debug("[Ingest] nodeId={} level={}->{} alertFired={}", req.nodeId(), previousLevel, newLevel, alertFired);
        return new IngestResponse(true, req.nodeId(), alertFired);
    }
}
