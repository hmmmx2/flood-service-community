package com.fyp.floodmonitoring.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles {@code community_posts.comments_count} with the actual
 * row count in {@code community_comments} on each boot. Earlier paths
 * (FK SET NULL on parent delete, partial rollbacks from notification
 * failures, manual cleanup) drifted the denormalized counter ahead of
 * reality — listings showed "2 Comments" while the detail page showed
 * "No comments yet". The service now always reads the live count, but
 * we also keep the legacy column honest so anything still touching it
 * (admin dashboards, mobile cache, etc.) doesn't propagate the drift.
 */
@Slf4j
@Component
@Order(50)
public class CommentCountReconciler implements CommandLineRunner {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            int aligned = em.createNativeQuery(
                    "UPDATE community_posts p " +
                    "SET comments_count = COALESCE(c.n, 0) " +
                    "FROM (SELECT post_id, COUNT(*)::int AS n " +
                    "      FROM community_comments " +
                    "      GROUP BY post_id) c " +
                    "WHERE p.id = c.post_id " +
                    "  AND p.comments_count IS DISTINCT FROM COALESCE(c.n, 0)"
            ).executeUpdate();

            int zeroed = em.createNativeQuery(
                    "UPDATE community_posts " +
                    "SET comments_count = 0 " +
                    "WHERE comments_count > 0 " +
                    "  AND id NOT IN (SELECT DISTINCT post_id FROM community_comments)"
            ).executeUpdate();

            int total = aligned + zeroed;
            if (total > 0) {
                log.info("[CommentCountReconciler] Reconciled {} posts ({} aligned, {} zeroed)",
                        total, aligned, zeroed);
            }
        } catch (Exception e) {
            // Self-healing is best-effort — never block startup.
            log.warn("[CommentCountReconciler] Skipped: {}", e.getMessage());
        }
    }
}
