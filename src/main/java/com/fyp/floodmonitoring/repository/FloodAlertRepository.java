package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.FloodAlert;
import com.fyp.floodmonitoring.entity.FloodSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FloodAlertRepository extends JpaRepository<FloodAlert, Long> {

    /**
     * Dedup check: returns count of alerts for this (nodeId, severity)
     * within the configured suppression window. Caller checks > 0.
     */
    @Query("SELECT COUNT(f) FROM FloodAlert f " +
           "WHERE f.nodeId = :nodeId AND f.severity = :severity AND f.createdAt > :since")
    long countRecentAlerts(String nodeId, FloodSeverity severity, LocalDateTime since);

    /** Unacknowledged alerts — used by the mobile badge count endpoint. */
    List<FloodAlert> findByAcknowledgedFalseOrderByCreatedAtDesc();

    /** Recent 24-hour history — used by the mobile alerts screen. */
    @Query("SELECT f FROM FloodAlert f WHERE f.createdAt > :since ORDER BY f.createdAt DESC")
    List<FloodAlert> findRecent(LocalDateTime since);
}
