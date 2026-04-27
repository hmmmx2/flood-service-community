package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.AnalyticsDataDto;
import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.repository.EventRepository;
import com.fyp.floodmonitoring.repository.NodeRepository;
import com.fyp.floodmonitoring.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.IntStream;

import static com.fyp.floodmonitoring.dto.response.AnalyticsDataDto.*;

/**
 * Builds the full analytics payload from aggregated SQL queries.
 * All heavy work is pushed to the database — the service only maps results.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final NodeRepository  nodeRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    @Cacheable("analytics")
    public AnalyticsDataDto getAnalytics() {

        // ── Stats ─────────────────────────────────────────────────────────────
        long activeCount = nodeRepository.countByIsDeadFalse();
        long alertCount  = eventRepository.countAlerts24h();
        long dataCount   = eventRepository.countDataPoints24h();

        List<StatDto> stats = List.of(
                new StatDto("Active Sensors", String.valueOf(activeCount), "radio-outline",
                        String.valueOf(activeCount)),
                new StatDto("Alerts (24h)", String.valueOf(alertCount), "alert-circle-outline",
                        String.valueOf(alertCount)),
                new StatDto("Data Points", GeoUtils.formatCount(dataCount), "stats-chart-outline",
                        "+" + GeoUtils.formatCount(dataCount)));

        // ── Weekly chart (last 7 days) ────────────────────────────────────────
        Map<String, Integer> dayMap = buildDayMap(eventRepository.countEventsByDayLast7Days());
        List<Integer> chartData = IntStream.rangeClosed(0, 6)
                .mapToObj(i -> {
                    String key = LocalDate.now().minusDays(6 - i)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE);
                    return dayMap.getOrDefault(key, 0);
                })
                .toList();

        // ── Monthly chart (last 5 months) ─────────────────────────────────────
        Map<String, Integer> monthMap5 = buildMonthMap(eventRepository.countEventsByMonthLast5Months());
        List<Integer> yearlyChartData = IntStream.rangeClosed(0, 4)
                .mapToObj(i -> {
                    String key = YearMonth.now().minusMonths(4 - i)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM"));
                    return monthMap5.getOrDefault(key, 0);
                })
                .toList();

        // ── Water level by node (top 10 by current level) ────────────────────
        List<WaterLevelDto> waterLevelByNode = nodeRepository
                .findAllByOrderByCurrentLevelDescNodeIdAsc()
                .stream()
                .limit(10)
                .filter(n -> !Boolean.TRUE.equals(n.getIsDead()))
                .map(n -> new WaterLevelDto(n.getNodeId(), safeLevel(n),
                        levelToStatus(safeLevel(n))))
                .toList();

        // ── Flood by state — queried from PostgreSQL, grouped by node.state ─────
        List<FloodByStateDto> floodByState = eventRepository.countAlertsByState()
                .stream()
                .map(row -> new FloodByStateDto(
                        (String)  row[0],
                        ((Number) row[1]).intValue()))
                .toList();

        // ── Recent events (last 10) ───────────────────────────────────────────
        List<RecentEventDto> recentEvents = eventRepository.findTop10RecentEvents()
                .stream()
                .map(row -> {
                    String nodeId    = (String) row[0];
                    int    level     = row[1] != null ? ((Number) row[1]).intValue() : 0;
                    Instant ts       = toInstant(row[2]);
                    boolean isAlert  = level >= 2;
                    String title     = isAlert
                            ? "Node " + nodeId + " High Water (Level " + level + ")"
                            : "Node " + nodeId + " Level Update";
                    return new RecentEventDto(title, GeoUtils.relativeTimeFull(ts),
                            isAlert ? "warning" : "info");
                })
                .toList();

        return new AnalyticsDataDto(stats, chartData, yearlyChartData,
                waterLevelByNode, floodByState, recentEvents);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Integer> buildDayMap(List<Object[]> rows) {
        Map<String, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    private Map<String, Integer> buildMonthMap(List<Object[]> rows) {
        Map<String, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    private int safeLevel(Node n) {
        return n.getCurrentLevel() != null ? n.getCurrentLevel() : 0;
    }

    private String levelToStatus(int level) {
        if (level >= 3) return "critical";
        if (level >= 2) return "warning";
        return "normal";
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant)  return (Instant) value;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        return Instant.now();
    }
}
