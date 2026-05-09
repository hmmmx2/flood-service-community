package com.fyp.floodmonitoring.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot startup migration that widens {@code users.avatar_url} from the
 * legacy {@code VARCHAR(500)} to {@code TEXT}.
 *
 * <p>Hibernate's {@code ddl-auto: update} does not alter existing column
 * types, so even after the entity moved to {@code columnDefinition = "TEXT"}
 * the live column on Neon stayed at 500 chars — and the new in-app file
 * uploader, which writes a 60-80 KB base64 data URL, was being rejected
 * with "value too long for type character varying(500)" surfacing as a
 * generic 500.</p>
 *
 * <p>Runs after {@link ApplicationReadyEvent} (so it never blocks Railway's
 * readiness probe) and {@code @Async} (so a slow ALTER doesn't pin the
 * event thread). The DDL is idempotent — re-running on a column already
 * typed as TEXT is a metadata no-op.</p>
 */
@Slf4j
@Component
public class AvatarColumnMigration {

    @PersistenceContext
    private EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    @Transactional
    public void widenAvatarUrlColumn() {
        try {
            em.createNativeQuery("ALTER TABLE users ALTER COLUMN avatar_url TYPE TEXT")
              .executeUpdate();
            log.info("[AvatarColumnMigration] users.avatar_url is TEXT");
        } catch (Exception e) {
            // Best effort — never block app readiness on a schema tweak.
            // The most common case where this throws is when avatar_url is
            // already TEXT, which is the desired end state.
            log.warn("[AvatarColumnMigration] ALTER skipped: {}", e.getMessage());
        }
    }
}
