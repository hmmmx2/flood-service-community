package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.entity.UserSavedLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserSavedLocationRepository
        extends JpaRepository<UserSavedLocation, UUID> {

    List<UserSavedLocation> findByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    /**
     * Resolver for the radius-aware notification fan-out. Returns every
     * user with at least one saved location whose centre is within its
     * own per-pin alert_radius_km of the given sensor coordinate.
     *
     * Haversine in raw SQL — fine for <100k pins which is well within
     * our scale. We pre-filter with a degree-bounding box (~1.0° per
     * 100 km at the equator → safe upper bound for our 50 km max
     * radius) so the trig is only computed for candidates that could
     * plausibly match.
     */
    @Query(value = """
        SELECT DISTINCT u.* FROM users u
        INNER JOIN user_saved_locations l ON l.user_id = u.id
        WHERE l.latitude  BETWEEN :nodeLat - 0.6 AND :nodeLat + 0.6
          AND l.longitude BETWEEN :nodeLng - 0.6 AND :nodeLng + 0.6
          AND (
              6371.0 * acos(
                  GREATEST(-1.0, LEAST(1.0,
                      cos(radians(l.latitude))   * cos(radians(:nodeLat)) *
                      cos(radians(:nodeLng) - radians(l.longitude)) +
                      sin(radians(l.latitude))   * sin(radians(:nodeLat))
                  ))
              )
          ) <= l.alert_radius_km
        """, nativeQuery = true)
    List<User> findUsersWithSavedLocationNearby(
            @Param("nodeLat") double nodeLat,
            @Param("nodeLng") double nodeLng);

    /**
     * Closest matching saved location for a given user / sensor pair.
     * Used to personalise the notification body ("Flood near your Home").
     * Returns the row whose centre is closest to the sensor.
     */
    @Query(value = """
        SELECT l.* FROM user_saved_locations l
        WHERE l.user_id = :userId
          AND l.latitude  BETWEEN :nodeLat - 0.6 AND :nodeLat + 0.6
          AND l.longitude BETWEEN :nodeLng - 0.6 AND :nodeLng + 0.6
          AND (
              6371.0 * acos(
                  GREATEST(-1.0, LEAST(1.0,
                      cos(radians(l.latitude))   * cos(radians(:nodeLat)) *
                      cos(radians(:nodeLng) - radians(l.longitude)) +
                      sin(radians(l.latitude))   * sin(radians(:nodeLat))
                  ))
              )
          ) <= l.alert_radius_km
        ORDER BY (
              6371.0 * acos(
                  GREATEST(-1.0, LEAST(1.0,
                      cos(radians(l.latitude))   * cos(radians(:nodeLat)) *
                      cos(radians(:nodeLng) - radians(l.longitude)) +
                      sin(radians(l.latitude))   * sin(radians(:nodeLat))
                  ))
              )
          ) ASC
        LIMIT 1
        """, nativeQuery = true)
    java.util.Optional<UserSavedLocation> findClosestForUser(
            @Param("userId") UUID userId,
            @Param("nodeLat") double nodeLat,
            @Param("nodeLng") double nodeLng);
}
