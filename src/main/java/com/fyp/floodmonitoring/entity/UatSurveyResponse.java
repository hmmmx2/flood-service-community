package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * UAT survey response — one row per submission, from either the community
 * site (end-user feedback) or the CRM (staff/admin feedback). The full
 * answer payload is stored as a JSON string in a TEXT column so the question
 * schema can evolve without a DB migration. Two top-level metrics
 * (satisfaction_score, recommend_score) are denormalised for fast filtering
 * and dashboard charts.
 *
 * Stored as TEXT (not Postgres jsonb) deliberately: the project does not
 * pull in hypersistence-utils, and TEXT gives us full portability with the
 * exact same query patterns we need (filter by score, list paged, export).
 *
 * @see migrations/005_uat_surveys.sql
 */
@Entity
@Table(name = "uat_survey_responses")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class UatSurveyResponse {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Nullable — anonymous submissions allowed (rare, but supported). */
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    /** Captured at submit time so the admin table can show a name even if
     *  the user changes their display name later. */
    @Column(name = "display_name", length = 120)
    private String displayName;

    /** "user" | "admin" | "both" — drives which question buckets the
     *  frontend rendered. Stored so the export can group by audience. */
    @Column(nullable = false, length = 40)
    private String role;

    /** "community" | "crm" — which app the response came from. */
    @Column(nullable = false, length = 20)
    private String source;

    /** Full answer payload as a JSON string — keys correspond to question
     *  IDs defined in the frontend survey schema. Persisted via Jackson
     *  serialisation in the service layer. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answers;

    @Column(name = "satisfaction_score")
    private Short satisfactionScore;

    @Column(name = "recommend_score")
    private Short recommendScore;

    /** "meets" | "partial" | "misses" — does the product meet the original
     *  business requirement, in the respondent's view? */
    @Column(name = "business_fit", length = 40)
    private String businessFit;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private Instant submittedAt = Instant.now();

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
}
