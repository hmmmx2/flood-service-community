package com.fyp.floodmonitoring.dto.response;

public record DashboardNodeRowDto(
        String id,
        String level,       // e.g. "2.5m"
        String area,
        String location,
        String state,
        String status,      // "Normal" | "Warning" | "Critical"
        String update,      // relative time, e.g. "5m ago"
        String timestamp    // formatted time, e.g. "02:30 PM"
) {}
