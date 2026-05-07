package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.UatSurveyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UatSurveyResponseRepository extends JpaRepository<UatSurveyResponse, UUID> {

    /** Admin moderation list — newest first, paginated. */
    Page<UatSurveyResponse> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    /** Filter by audience for the dashboard role-tabs. */
    Page<UatSurveyResponse> findByRoleOrderBySubmittedAtDesc(String role, Pageable pageable);

    Page<UatSurveyResponse> findBySourceOrderBySubmittedAtDesc(String source, Pageable pageable);

    /** Bulk export — no pagination. Bounded internally to 5000 rows so an
     *  Excel/CSV export of years of responses doesn't OOM the service. */
    @Query("SELECT u FROM UatSurveyResponse u ORDER BY u.submittedAt DESC")
    List<UatSurveyResponse> findAllForExport(Pageable pageable);
}
