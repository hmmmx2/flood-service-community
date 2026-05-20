package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.entity.WebPushSubscription;
import com.fyp.floodmonitoring.repository.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Service-to-service endpoints consumed by the Vercel community BFF.
 *
 * <p>All routes here are gated by the X-Internal-Key header (see
 * {@link com.fyp.floodmonitoring.security.InternalApiKeyFilter}) which
 * grants {@code ROLE_SERVICE}. Operators / end users with a normal
 * JWT cannot reach these routes — that's intentional, the data is
 * cross-user (every subscriber's push endpoint) and only the Vercel
 * push-dispatch route needs it.</p>
 *
 * <pre>
 * GET    /internal/web-push-subscriptions          — list all (for fan-out)
 * DELETE /internal/web-push-subscriptions?endpoint=… — drop a stale endpoint (410 Gone cleanup)
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/internal/web-push-subscriptions")
@RequiredArgsConstructor
public class InternalPushController {

    private final WebPushSubscriptionRepository webPushRepository;

    /**
     * Returns every stored Web Push subscription so the Vercel push
     * dispatcher can fan out to all of them. The Vercel side rate-
     * limits dispatches (5-min per node+alert) before this is even
     * called, so this list is read at most a handful of times per
     * minute even under heavy alert load.
     *
     * <p>Returned shape matches what the {@code web-push} npm package
     * expects for {@code sendNotification(subscription, payload)}:
     * <pre>
     * { endpoint: string, keys: { p256dh: string, auth: string } }
     * </pre>
     */
    @GetMapping
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<List<SubscriptionDto>> listAll() {
        List<SubscriptionDto> all = webPushRepository.findAll().stream()
                .map(SubscriptionDto::from)
                .toList();
        log.debug("[InternalPush] listAll → {} subscription(s)", all.size());
        return ResponseEntity.ok(all);
    }

    /**
     * Removes a single subscription by endpoint URL. Called by the
     * Vercel push dispatcher when {@code web-push.sendNotification}
     * returns HTTP 410 Gone (subscription expired — typical when a
     * user uninstalled the PWA or cleared site data). Idempotent.
     */
    @DeleteMapping
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Void> deleteByEndpoint(@RequestParam String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        int deleted = webPushRepository.deleteByEndpoint(endpoint);
        log.info("[InternalPush] expired endpoint cleanup: deleted={} endpoint={}",
                deleted, endpoint);
        return ResponseEntity.noContent().build();
    }

    /** JSON wire shape — matches `web-push` npm's PushSubscription contract. */
    public record SubscriptionDto(String endpoint, Keys keys) {
        public record Keys(String p256dh, String auth) {}

        static SubscriptionDto from(WebPushSubscription sub) {
            return new SubscriptionDto(
                    sub.getEndpoint(),
                    new Keys(sub.getP256dh(), sub.getAuthKey())
            );
        }
    }
}
