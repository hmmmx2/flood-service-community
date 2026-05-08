package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, UUID> {

    @Query("""
           SELECT c FROM EmailVerificationCode c
            WHERE c.userId = :userId AND c.code = :code AND c.used = false
           ORDER BY c.createdAt DESC
           LIMIT 1
           """)
    Optional<EmailVerificationCode> findLatestUnused(UUID userId, String code);

    @Modifying
    @Query("UPDATE EmailVerificationCode c SET c.used = true WHERE c.userId = :userId AND c.used = false")
    void invalidateAllForUser(UUID userId);
}
