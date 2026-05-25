package com.fyp.floodmonitoring.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Startup-time guard for production-unsafe configuration.
 *
 * Production fails closed for schema auto-mutation and missing JWT secrets.
 * Local/dev/test environments still log the active settings so developers can
 * run with ddl-auto=update or create-drop without fighting the guard.
 */
@Slf4j
@Configuration
public class StartupConfigGuard {

    @Value("${spring.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${app.environment:development}")
    private String environment;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.jwt.refresh-secret:}")
    private String jwtRefreshSecret;

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${ai.service.api-key:}")
    private String aiServiceApiKey;

    @Bean
    public ApplicationRunner startupConfigAuditRunner() {
        return args -> {
            boolean looksProd =
                    "prod".equalsIgnoreCase(activeProfile)
                            || "production".equalsIgnoreCase(activeProfile)
                            || "production".equalsIgnoreCase(environment)
                            || "prod".equalsIgnoreCase(environment);

            boolean mutatesSchema =
                    "update".equalsIgnoreCase(ddlAuto)
                            || "create".equalsIgnoreCase(ddlAuto)
                            || "create-drop".equalsIgnoreCase(ddlAuto);

            if (looksProd && mutatesSchema) {
                throw new IllegalStateException(
                        "[StartupConfigGuard] hibernate.ddl-auto=" + ddlAuto
                                + " on profile=" + activeProfile + " (env=" + environment + "). "
                                + "Set HIBERNATE_DDL_AUTO=validate in production and apply explicit migrations.");
            }

            if (looksProd && (jwtSecret.isBlank() || jwtRefreshSecret.isBlank())) {
                throw new IllegalStateException(
                        "[StartupConfigGuard] JWT_SECRET and JWT_REFRESH_SECRET must be configured in production.");
            }

            if (looksProd && "*".equals(allowedOrigins.trim())) {
                throw new IllegalStateException(
                        "[StartupConfigGuard] app.cors.allowed-origins must not be '*' in production.");
            }

            if (looksProd && aiServiceApiKey.isBlank()) {
                throw new IllegalStateException(
                        "[StartupConfigGuard] AI_SERVICE_API_KEY must be configured in production.");
            }

            log.info("[StartupConfigGuard] hibernate.ddl-auto={} (profile={}, env={}) - OK",
                    ddlAuto, activeProfile, environment);
        };
    }
}
