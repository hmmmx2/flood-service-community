package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSettingRepository extends JpaRepository<UserSetting, UUID> {

    List<UserSetting> findByUserIdOrderByKeyAsc(UUID userId);

    Optional<UserSetting> findByUserIdAndKey(UUID userId, String key);

    @Modifying
    @Query(value = """
           INSERT INTO user_settings (user_id, key, enabled)
           VALUES (:userId, :key, false)
           ON CONFLICT (user_id, key) DO NOTHING
           """, nativeQuery = true)
    void upsertDefault(UUID userId, String key);
}
