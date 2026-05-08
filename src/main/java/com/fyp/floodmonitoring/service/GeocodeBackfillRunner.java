package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Backfills the new {@code nodes.address / area / state} columns from
 * Google Maps reverse-geocoding for every node that's still missing
 * an address.
 *
 * Two ways to run it:
 *   1. Boot-time: set environment variable {@code GEOCODE_BACKFILL=true}
 *      and (re)start the service; the runner kicks in after Spring is
 *      ready.
 *   2. On-demand admin endpoint: POST /admin/nodes/geocode-backfill
 *      (see {@link com.fyp.floodmonitoring.controller.AdminGeocodingController}).
 *
 * Idempotent — only touches rows where {@code address IS NULL}, so it
 * is safe to re-run after a fresh sensor onboarding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeocodeBackfillRunner implements CommandLineRunner {

    /** Soft rate limit — Google's default quota is 50 req/sec. We
     *  sleep 25 ms between calls (~40 rps) to stay comfortably under. */
    private static final long SLEEP_MS = 25;

    private final NodeRepository nodeRepo;
    private final GeocodingService geocodingService;

    @Value("${app.geocode-backfill:false}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("[Geocode] Boot-time backfill disabled (set GEOCODE_BACKFILL=true to enable).");
            return;
        }
        int updated = backfillAll();
        log.info("[Geocode] Boot-time backfill complete — updated {} node(s).", updated);
    }

    /** Public entry for the admin controller. Returns the number of
     *  nodes that received a non-empty geocode result. */
    @Transactional
    public int backfillAll() {
        if (!geocodingService.isAvailable()) {
            log.warn("[Geocode] Backfill skipped — GeocodingService is not configured.");
            return 0;
        }
        List<Node> all = nodeRepo.findAll();
        int updated = 0;
        for (Node n : all) {
            if (n.getAddress() != null && !n.getAddress().isBlank()) continue;  // idempotent
            if (n.getLatitude() == null || n.getLongitude() == null) continue;

            Optional<GeocodingService.ReverseGeocode> r =
                    geocodingService.reverseGeocode(n.getLatitude(), n.getLongitude());
            if (r.isEmpty()) continue;

            GeocodingService.ReverseGeocode g = r.get();
            n.setAddress(g.address());
            if (g.area()  != null && !g.area().isBlank())  n.setArea(g.area());
            if (g.state() != null && !g.state().isBlank()) n.setState(g.state());
            updated++;

            try { Thread.sleep(SLEEP_MS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
        }
        nodeRepo.saveAll(all);
        return updated;
    }
}
