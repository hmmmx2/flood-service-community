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
}
