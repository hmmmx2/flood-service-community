package com.fyp.floodmonitoring.dto.response;

import java.util.List;

public record AnalyticsDataDto(
        List<StatDto>         stats,
        List<Integer>         chartData,
        List<Integer>         yearlyChartData,
        List<WaterLevelDto>   waterLevelByNode,
        List<FloodByStateDto> floodByState,
        List<RecentEventDto>  recentEvents
) {

    public record StatDto(String label, String value, String icon, String trend) {}

    public record WaterLevelDto(String nodeId, int level, String status) {}

    public record FloodByStateDto(String state, int total) {}

    public record RecentEventDto(String title, String timestamp, String type) {}
}
