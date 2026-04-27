package com.fyp.floodmonitoring.config;

import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a single admin account on startup using credentials supplied via
 * environment variables ({@code ADMIN_SEED_EMAIL} / {@code ADMIN_SEED_PASSWORD}).
 *
 * <p>If either variable is not set the seeding step is skipped — no
 * credentials are ever stored in source code.</p>
 *
 * <p>If the account already exists it is promoted to {@code role = "admin"}
 * without touching its password.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.seed-email:}")
    private String seedEmail;

    @Value("${app.admin.seed-password:}")
    private String seedPassword;

    @Value("${app.admin.seed-first-name:Admin}")
    private String seedFirstName;

    @Value("${app.admin.seed-last-name:User}")
    private String seedLastName;

    @Override
    public void run(String... args) {
        if (seedEmail.isBlank() || seedPassword.isBlank()) {
            log.info("DataInitializer: ADMIN_SEED_EMAIL / ADMIN_SEED_PASSWORD not set — skipping admin seed");
            return;
        }

        String email = seedEmail.toLowerCase().trim();

        userRepository.findByEmail(email).ifPresentOrElse(
            existing -> {
                if (!"admin".equals(existing.getRole())) {
                    existing.setRole("admin");
                    userRepository.save(existing);
                    log.info("DataInitializer: promoted {} to admin", email);
                }
            },
            () -> {
                User admin = User.builder()
                        .firstName(seedFirstName.trim())
                        .lastName(seedLastName.trim())
                        .email(email)
                        .passwordHash(passwordEncoder.encode(seedPassword))
                        .role("admin")
                        .build();
                userRepository.save(admin);
                log.info("DataInitializer: created admin account {}", email);
            }
        );
    }
}
