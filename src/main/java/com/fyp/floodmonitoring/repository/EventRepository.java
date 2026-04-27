package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@code events} table (200k+ rows).
 * All heavy-aggregation queries use native SQL for optimal performance.
 */
@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    // ── Feed (cursor-based pagination) ────────────────────────────────────────

    @Query(value = "SELECT * FROM events ORDER BY created_at DESC LIMIT :limit",
           nativeQuery = true)
    List<Event> findFirstPage(@Param("limit") int limit);

    @Query(value = "SELECT * FROM events WHERE created_at < :cursor ORDER BY created_at DESC LIMIT :limit",
           nativeQuery = true)
    List<Event> findPageAfterCursor(@Param("cursor") Instant cursor, @Param("limit") int limit);

    // ── Analytics — counts ────────────────────────────────────────────────────

    @Query(value = "SELECT COUNT(*) FROM events WHERE new_level >= 2 AND created_at > NOW() - INTERVAL '24 hours'",
           nativeQuery = true)
    long countAlerts24h();

    @Query(value = "SELECT COUNT(*) FROM events WHERE created_at > NOW() - INTERVAL '24 hours'",
           nativeQuery = true)
    long countDataPoints24h();

    @Query(value = "SELECT COUNT(*) FROM events WHERE new_level >= 2",
           nativeQuery = true)
    long countTotalAlerts();

    // ── Analytics — chart data ────────────────────────────────────────────────

    /**
     * Returns rows of [day VARCHAR, count BIGINT] for the last 7 days.
     */
    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('day', created_at), 'YYYY-MM-DD') AS day,
                   COUNT(*) AS count
              FROM events
             WHERE created_at > NOW() - INTERVAL '7 days'
             GROUP BY day
             ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> countEventsByDayLast7Days();

    /**
     * Returns rows of [month VARCHAR, count BIGINT] for the last 5 months.
     */
    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS month,
                   COUNT(*) AS count
              FROM events
             WHERE created_at > NOW() - INTERVAL '5 months'
             GROUP BY month
             ORDER BY month ASC
            """, nativeQuery = true)
    List<Object[]> countEventsByMonthLast5Months();

    /**
     * Returns rows of [month VARCHAR, count BIGINT] for the last 12 months.
     */
    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS month,
                   COUNT(*) AS count
              FROM events
             WHERE created_at > NOW() - INTERVAL '12 months'
             GROUP BY month
             ORDER BY month ASC
            """, nativeQuery = true)
    List<Object[]> countEventsByMonthLast12Months();

    /**
     * Returns rows of [year VARCHAR, count BIGINT] for the last 5 years.
     */
    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('year', created_at), 'YYYY') AS year,
                   COUNT(*) AS count
              FROM events
             WHERE created_at > NOW() - INTERVAL '5 years'
             GROUP BY year
             ORDER BY year ASC
            """, nativeQuery = true)
    List<Object[]> countEventsByYearLast5Years();

    // ── Analytics — recent events ─────────────────────────────────────────────

    @Query(value = "SELECT node_id, new_level, created_at FROM events ORDER BY created_at DESC LIMIT 10",
           nativeQuery = true)
    List<Object[]> findTop10RecentEvents();

    // ── Analytics — flood events by state (joins nodes for state label) ───────

    /**
     * Returns rows of [state VARCHAR, alert_count BIGINT] for all states
     * that have at least one alert-level event (new_level >= 2).
     * Uses the node's {@code state} column as the geographic label.
     */
    @Query(value = """
            SELECT n.state,
                   COUNT(*) AS alert_count
              FROM events e
              JOIN nodes  n ON e.node_id = n.node_id
             WHERE e.new_level >= 2
             GROUP BY n.state
             ORDER BY alert_count DESC
            """, nativeQuery = true)
    List<Object[]> countAlertsByState();
}
