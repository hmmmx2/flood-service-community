package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :now WHERE u.id = :id")
    void updateLastLogin(UUID id, Instant now);

    /** Find all users who have a push token registered and have notifications enabled. */
    @Query(value = """
            SELECT u.* FROM users u
            INNER JOIN user_settings s ON s.user_id = u.id
            WHERE u.push_token IS NOT NULL
              AND s.key = :settingKey
              AND s.enabled = true
            """, nativeQuery = true)
    java.util.List<User> findUsersWithPushTokenAndSetting(String settingKey);

    @Modifying
    @Query("UPDATE User u SET u.pushToken = :token WHERE u.id = :id")
    void updatePushToken(UUID id, String token);

    /** Find users who have opted in to email flood alerts. */
    @Query(value = """
            SELECT u.* FROM users u
            INNER JOIN user_settings s ON s.user_id = u.id
            WHERE s.key = 'emailAlerts'
              AND s.enabled = true
            """, nativeQuery = true)
    java.util.List<User> findUsersWithEmailAlertsEnabled();

    /**
     * Radius-aware email recipient resolver — used by the
     * FloodAlertFanOutListener so an alert about a sensor in Kota Kinabalu
     * doesn't email a resident in Kuala Lumpur.
     *
     * Returns every emailAlerts=true user who EITHER:
     *   (a) has no saved locations yet — legacy behaviour preserved, the
     *       "all subscribers" blast still works for users who haven't
     *       configured pins yet, OR
     *   (b) has at least one saved location whose centre is within its
     *       own per-pin alert_radius_km of the sensor.
     *
     * Bounding-box pre-filter (±0.6° ≈ 66 km, comfortably above the
     * 50 km max radius) makes the trig only run for plausible matches.
     */
    @Query(value = """
            SELECT DISTINCT u.* FROM users u
            INNER JOIN user_settings s ON s.user_id = u.id
                AND s.key = 'emailAlerts' AND s.enabled = true
            WHERE
              NOT EXISTS (SELECT 1 FROM user_saved_locations l WHERE l.user_id = u.id)
              OR EXISTS (
                  SELECT 1 FROM user_saved_locations l
                  WHERE l.user_id = u.id
                    AND l.latitude  BETWEEN :nodeLat - 0.6 AND :nodeLat + 0.6
                    AND l.longitude BETWEEN :nodeLng - 0.6 AND :nodeLng + 0.6
                    AND (
                      6371.0 * acos(GREATEST(-1.0, LEAST(1.0,
                        cos(radians(l.latitude)) * cos(radians(:nodeLat)) *
                        cos(radians(:nodeLng) - radians(l.longitude)) +
                        sin(radians(l.latitude)) * sin(radians(:nodeLat)))))
                    ) <= l.alert_radius_km
              )
            """, nativeQuery = true)
    java.util.List<User> findEmailSubscribersForFloodAt(
            @org.springframework.data.repository.query.Param("nodeLat") double nodeLat,
            @org.springframework.data.repository.query.Param("nodeLng") double nodeLng);

    /**
     * Channel-agnostic recipient resolver for the multichannel dispatcher.
     * Returns every user who:
     *   • has at least one of {emailAlerts, smsAlerts, whatsappAlerts,
     *     inAppAlerts} enabled in user_settings, AND
     *   • EITHER has no saved-location pins (legacy "all subscribers"
     *     fallback), OR has a saved location whose centre is within its
     *     own per-pin alert_radius_km of the alerting sensor, OR has
     *     starred (favourited) the alerting sensor regardless of radius.
     *
     * The dispatcher then looks up each user's individual channel flags
     * (and phone) before deciding what to send. Same bounding-box pre-
     * filter as the email-only query (±0.6° ≈ 66 km).
     */
    @Query(value = """
            SELECT DISTINCT u.* FROM users u
            INNER JOIN user_settings s ON s.user_id = u.id
                AND s.key IN ('emailAlerts', 'smsAlerts', 'whatsappAlerts', 'inAppAlerts')
                AND s.enabled = true
            WHERE
              NOT EXISTS (SELECT 1 FROM user_saved_locations l WHERE l.user_id = u.id)
              OR EXISTS (
                  SELECT 1 FROM user_saved_locations l
                  WHERE l.user_id = u.id
                    AND l.latitude  BETWEEN :nodeLat - 0.6 AND :nodeLat + 0.6
                    AND l.longitude BETWEEN :nodeLng - 0.6 AND :nodeLng + 0.6
                    AND (
                      6371.0 * acos(GREATEST(-1.0, LEAST(1.0,
                        cos(radians(l.latitude)) * cos(radians(:nodeLat)) *
                        cos(radians(:nodeLng) - radians(l.longitude)) +
                        sin(radians(l.latitude)) * sin(radians(:nodeLat)))))
                    ) <= l.alert_radius_km
              )
              OR EXISTS (
                  SELECT 1 FROM user_favourite_nodes f
                  JOIN nodes n ON n.id = f.node_id
                  WHERE f.user_id = u.id AND n.node_id = :alertNodeIdStr
              )
            """, nativeQuery = true)
    java.util.List<User> findNotificationSubscribersForFloodAt(
            @org.springframework.data.repository.query.Param("nodeLat")        double nodeLat,
            @org.springframework.data.repository.query.Param("nodeLng")        double nodeLng,
            @org.springframework.data.repository.query.Param("alertNodeIdStr") String alertNodeIdStr);
}
