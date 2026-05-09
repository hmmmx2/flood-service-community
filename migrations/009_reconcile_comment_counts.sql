-- Reconciles community_posts.comments_count with the actual rows in
-- community_comments. Some earlier paths (FK SET NULL on parent delete,
-- partial rollbacks, manual cleanup) left the denormalized counter
-- ahead of reality, which made the listing show "2 Comments" while the
-- detail page showed "No comments yet". The service now reads the live
-- count via native SQL on every request, so this migration just makes
-- the legacy column honest for any consumer that still looks at it.

UPDATE community_posts p
SET comments_count = COALESCE(c.n, 0)
FROM (
    SELECT post_id, COUNT(*)::int AS n
    FROM community_comments
    GROUP BY post_id
) c
WHERE p.id = c.post_id
  AND p.comments_count IS DISTINCT FROM COALESCE(c.n, 0);

UPDATE community_posts
SET comments_count = 0
WHERE comments_count > 0
  AND id NOT IN (SELECT DISTINCT post_id FROM community_comments);
