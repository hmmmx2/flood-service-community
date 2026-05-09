package com.fyp.floodmonitoring.config;

import com.fyp.floodmonitoring.entity.EmailSender;
import com.fyp.floodmonitoring.repository.EmailSenderRepository;
import com.fyp.floodmonitoring.service.EmailSenderResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the {@code email_senders} table on first startup so the
 * resolver always has rows to read. Idempotent — only inserts a
 * purpose row when one is not already present, so editing a from
 * address in production is preserved across deploys.
 *
 * <p>Hibernate's ddl-auto:update creates the table from the entity
 * mapping; this runner is what fills it.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSenderInitializer implements CommandLineRunner {

    private final EmailSenderRepository repo;

    @Override
    public void run(String... args) {
        seed(EmailSenderResolver.REGISTRATION,   "noreply@floodwatch.app", "FloodWatch");
        seed(EmailSenderResolver.PASSWORD_RESET, "noreply@floodwatch.app", "FloodWatch");
        seed(EmailSenderResolver.FLOOD_ALERT,    "alerts@floodwatch.app",  "FloodWatch Alerts");
        seed(EmailSenderResolver.BROADCAST,      "alerts@floodwatch.app",  "FloodWatch Alerts");
    }

    private void seed(String purpose, String fromAddress, String displayName) {
        if (repo.findByPurposeIgnoreCaseAndActiveTrue(purpose).isPresent()) return;
        repo.save(EmailSender.builder()
                .purpose(purpose)
                .fromAddress(fromAddress)
                .displayName(displayName)
                .active(true)
                .build());
        log.info("[EmailSenderInitializer] Seeded {} → {}", purpose, fromAddress);
    }
}
