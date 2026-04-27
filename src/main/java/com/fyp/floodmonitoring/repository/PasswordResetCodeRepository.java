package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    @Query("""
           SELECT p FROM PasswordResetCode p
            WHERE p.userId = :userId AND p.code = :code AND p.used = false
           ORDER BY p.createdAt DESC
           LIMIT 1
           """)
    Optional<PasswordResetCode> findLatestUnused(UUID userId, String code);

    @Query("""
           SELECT p FROM PasswordResetCode p
            WHERE p.userId = :userId AND p.verified = true AND p.used = false
           ORDER BY p.createdAt DESC
           LIMIT 1
           """)
    Optional<PasswordResetCode> findLatestVerifiedUnused(UUID userId);

    @Modifying
    @Query("UPDATE PasswordResetCode p SET p.used = true WHERE p.userId = :userId AND p.used = false")
    void invalidateAllForUser(UUID userId);
}
