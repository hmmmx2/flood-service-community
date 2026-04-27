package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.DashboardNodeRowDto;
import com.fyp.floodmonitoring.dto.response.DashboardTimeSeriesDto;
import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.repository.EventRepository;
import com.fyp.floodmonitoring.repository.NodeRepository;
import com.fyp.floodmonitoring.util.GeoUtils;
import com.fyp.floodmonitoring.util.WaterLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Kuching");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("hh:mm a").withZone(DISPLAY_ZONE);

    private final NodeRepository  nodeRepository;
    private final EventRepository eventRepository;

    // ── Node status table ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DashboardNodeRowDto> getDashboardNodes() {
        return nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc()
                .stream()
                .map(this::toRowDto)
                .toList();
    }

    private DashboardNodeRowDto toRowDto(Node n) {
        int level          = n.getCurrentLevel() != null ? n.getCurrentLevel() : 0;
        Instant lastUpdated = n.getLastUpdated() != null ? n.getLastUpdated() : Instant.now();

        return new DashboardNodeRowDto(
                n.getId().toString(),
                WaterLevel.toLabel(level),
                n.getArea(),
                n.getLocation(),
                n.getState(),
                resolveDisplayStatus(level, n.getIsDead()),
                GeoUtils.relativeTime(lastUpdated),
                TIME_FMT.format(lastUpdated));
    }

    private String resolveDisplayStatus(int level, Boolean isDead) {
        if (Boolean.TRUE.equals(isDead) || level <= 1) return "Normal";
        if (level == 2) return "Warning";
        return "Critical";
    }

    // ── Time-series chart data ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardTimeSeriesDto getTimeSeries() {

        // Monthly counts — last 12 months
        Map<String, Integer> monthMap = buildMap(eventRepository.countEventsByMonthLast12Months(), 0);
        List<Integer> monthly = IntStream.rangeClosed(0, 11)
                .mapToObj(i -> {
                    String key = YearMonth.now().minusMonths(11 - i)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM"));
                    return monthMap.getOrDefault(key, 0);
                })
                .toList();

        // Yearly counts — last 5 years
        Map<String, Integer> yearMap = buildMap(eventRepository.countEventsByYearLast5Years(), 0);
        List<Integer> yearly = IntStream.rangeClosed(0, 4)
                .mapToObj(i -> {
                    String key = String.valueOf(Year.now().getValue() - (4 - i));
                    return yearMap.getOrDefault(key, 0);
                })
                .toList();

        return new DashboardTimeSeriesDto(monthly, yearly);
    }

    private Map<String, Integer> buildMap(List<Object[]> rows, int keyIdx) {
        Map<String, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }
}
