package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :now WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Trim old rows so the table doesn't grow unbounded — keep the most
     * recent 200 per user. Called from a scheduled job in NotificationService.
     */
    @Modifying
    @Query(value = """
            DELETE FROM user_notifications
             WHERE id IN (
               SELECT id FROM (
                 SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC) AS rn
                   FROM user_notifications
               ) t
               WHERE rn > 200
             )
            """, nativeQuery = true)
    int trimToMostRecent200PerUser();
}
