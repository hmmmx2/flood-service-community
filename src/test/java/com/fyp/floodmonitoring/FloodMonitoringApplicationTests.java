package com.fyp.floodmonitoring;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Full-application context load test.
 *
 * <p>Requires a live database (Neon PostgreSQL) because {@code DataInitializer} and all JPA
 * repositories must be wired. Run this as an integration test only — not in the normal
 * unit-test CI pass. Start the backend with a valid {@code DATABASE_URL} env var and then
 * execute {@code mvnw test -Dgroups=integration} to enable.</p>
 */
@Disabled("Integration test — requires a live Neon DB. Remove @Disabled and set DATABASE_URL to run.")
@SpringBootTest
@TestPropertySource(properties = {
        "app.jwt.secret=test_secret_key_at_least_32_chars_long_for_hmac",
        "app.jwt.refresh-secret=test_refresh_secret_key_also_32_chars_long",
        "app.jwt.access-token-expiry-ms=900000",
        "app.jwt.refresh-token-expiry-ms=604800000",
        "app.environment=test"
})
class FloodMonitoringApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context can be assembled correctly
    }
}
