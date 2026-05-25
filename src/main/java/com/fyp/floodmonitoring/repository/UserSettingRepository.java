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

    /**
     * Idempotently seed a default (disabled) setting row for a user.
     *
     * <p>Deliberately avoids two hidden DB-feature dependencies that the
     * previous {@code ON CONFLICT (user_id, key) DO NOTHING} form relied
     * on — both of which Hibernate's {@code ddl-auto=update} does NOT
     * reliably provide, and which silently went missing when the database
     * was migrated Neon→Railway, breaking new-user registration with a
     * 500:</p>
     * <ol>
     *   <li><b>The {@code id} default.</b> The entity generates its UUID
     *       app-side via {@code @UuidGenerator}, so Hibernate creates
     *       {@code id uuid NOT NULL} with <i>no</i> DB default. A native
     *       INSERT that omits {@code id} then violates NOT NULL — so we
     *       supply it explicitly with {@code gen_random_uuid()}
     *       (built-in on PostgreSQL 13+).</li>
     *   <li><b>The composite UNIQUE constraint.</b> {@code ON CONFLICT
     *       (user_id, key)} requires a matching unique index to exist;
     *       if it doesn't, Postgres throws "no unique or exclusion
     *       constraint matching the ON CONFLICT specification". We use
     *       {@code WHERE NOT EXISTS} instead, which needs no constraint
     *       and is still idempotent for one-shot default seeding.</li>
     * </ol>
     */
    @Modifying
    @Query(value = """
           INSERT INTO user_settings (id, user_id, key, enabled)
           SELECT gen_random_uuid(), :userId, :key, false
            WHERE NOT EXISTS (
                  SELECT 1 FROM user_settings
                   WHERE user_id = :userId AND key = :key
           )
           """, nativeQuery = true)
    void upsertDefault(UUID userId, String key);

    @Modifying
    @Query("DELETE FROM UserSetting s WHERE s.userId = :userId")
    void deleteByUserId(UUID userId);
}
