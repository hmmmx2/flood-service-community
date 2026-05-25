package com.fyp.floodmonitoring.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Widens columns the in-app uploader writes data: URLs to. Hibernate's
 * {@code ddl-auto: update} does not alter existing column types, so a
 * legacy {@code VARCHAR(500)} stays narrow and rejects the base64
 * payload with "value too long" — which the global exception handler
 * renders as a generic 500.
 *
 * <p>Each ALTER runs on its own JDBC connection in auto-commit mode so
 * a failure on one cannot poison the next. The DDL is idempotent —
 * {@code TEXT → TEXT} is a metadata no-op on Postgres.</p>
 *
 * <p>Runs after {@link ApplicationReadyEvent} and {@code @Async} so it
 * never blocks boot or the Railway readiness probe.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarColumnMigration {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void widenUploaderColumns() {
        widen("users", "avatar_url");
        // Blog hero images are uploaded as base64 data: URLs via the CRM
        // uploader; the legacy VARCHAR(500) overflowed and blocked blog
        // creation with a generic 500. Self-heal here so we never depend on
        // running a manual ALTER against the right (Neon) database by hand.
        widen("blogs", "image_url");
    }

    private void widen(String table, String column) {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.executeUpdate("ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE TEXT");
            log.info("[AvatarColumnMigration] {}.{} is TEXT", table, column);
        } catch (Exception e) {
            log.warn("[AvatarColumnMigration] ALTER {}.{} skipped: {}", table, column, e.getMessage());
        }
    }
}
