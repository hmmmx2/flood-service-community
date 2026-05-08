package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.ContentReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContentReportRepository extends JpaRepository<ContentReport, UUID> {

    /** Admin moderation list — newest pending first, then everything else by created_at desc. */
    @Query("""
           SELECT r FROM ContentReport r
           ORDER BY (CASE WHEN r.status = 'pending' THEN 0 ELSE 1 END), r.createdAt DESC
           """)
    Page<ContentReport> findAllForModeration(Pageable pageable);

    @Query("SELECT COUNT(r) FROM ContentReport r WHERE r.status = 'pending'")
    long countPending();

    /** Used by the admin "view all reports for THIS post" deep-link. */
    Page<ContentReport> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType, UUID targetId, Pageable pageable);

    /**
     * Same user can't spam-report the same post/comment more than once
     * (deduplication only — admins still see all reports across users).
     */
    boolean existsByReporterIdAndTargetTypeAndTargetId(
            UUID reporterId, String targetType, UUID targetId);

    /** Used by the cascading delete when a post/comment is removed. */
    @Query("DELETE FROM ContentReport r WHERE r.targetType = :targetType AND r.targetId = :targetId")
    @org.springframework.data.jpa.repository.Modifying
    int deleteByTarget(@Param("targetType") String targetType, @Param("targetId") UUID targetId);
}
