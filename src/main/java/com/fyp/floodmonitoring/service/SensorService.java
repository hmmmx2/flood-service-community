package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.SensorNodeDto;
import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.repository.NodeRepository;
import com.fyp.floodmonitoring.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Returns all IoT sensor nodes formatted as {@link SensorNodeDto} for the map view.
 * Distance is calculated from Kuching city centre using the Haversine formula.
 */
@Service
@RequiredArgsConstructor
public class SensorService {

    private final NodeRepository nodeRepository;

    @Transactional(readOnly = true)
    public List<SensorNodeDto> getAllSensors() {
        return nodeRepository.findAllByOrderByNodeIdAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    private SensorNodeDto toDto(Node n) {
        double dist = GeoUtils.haversineKm(
                GeoUtils.KUCHING_LAT, GeoUtils.KUCHING_LON,
                n.getLatitude(), n.getLongitude());

        int level = n.getCurrentLevel() != null ? n.getCurrentLevel() : 0;
        boolean dead = Boolean.TRUE.equals(n.getIsDead());

        return new SensorNodeDto(
                n.getId().toString(),
                n.getNodeId(),
                n.getName() != null ? n.getName() : "Node " + n.getNodeId(),
                resolveStatus(level, dead),
                String.format("%.1f km", dist),
                List.of(n.getLongitude(), n.getLatitude()),
                n.getArea()      != null ? n.getArea()     : "Kuching",
                n.getLocation()  != null ? n.getLocation() : "",
                n.getState()     != null ? n.getState()    : "Sarawak",
                level,
                dead,
                formatInstant(n.getLastUpdated()),
                formatInstant(n.getCreatedAt()));
    }

    private String resolveStatus(int level, boolean isDead) {
        if (isDead)      return "inactive";
        if (level >= 3)  return "warning";   // critical is also shown as warning-level alert
        if (level >= 2)  return "warning";
        return "active";
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : Instant.now().toString();
    }
}
