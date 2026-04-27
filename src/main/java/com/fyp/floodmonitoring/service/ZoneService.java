package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.ZoneDto;
import com.fyp.floodmonitoring.entity.Zone;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Returns flood risk zones with GeoJSON boundaries (SCRUM-106). */
@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    @Cacheable("zones")
    public List<ZoneDto> getAll() {
        return zoneRepository.findAllByOrderByRiskLevelDescNameAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ZoneDto getById(UUID id) {
        return zoneRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> AppException.notFound("Zone not found: " + id));
    }

    private ZoneDto toDto(Zone z) {
        return new ZoneDto(
                z.getId().toString(),
                z.getName(),
                z.getRiskLevel(),
                z.getBoundary(),
                z.getUpdatedAt() != null ? z.getUpdatedAt().toString() : null);
    }
}
