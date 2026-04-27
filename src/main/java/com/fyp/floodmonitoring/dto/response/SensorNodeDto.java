package com.fyp.floodmonitoring.dto.response;

import java.util.List;

public record SensorNodeDto(
        String id,
        String nodeId,          // business key, e.g. "102503180"
        String name,
        String status,          // "active" | "inactive" | "warning"
        String distance,        // e.g. "3.2 km"
        List<Double> coordinate,// [longitude, latitude]
        String area,
        String location,
        String state,
        Integer currentLevel,   // 0=dry 1=normal 2=warning 3=critical
        Boolean isDead,
        String lastUpdated,     // ISO-8601
        String createdAt        // ISO-8601
) {}
