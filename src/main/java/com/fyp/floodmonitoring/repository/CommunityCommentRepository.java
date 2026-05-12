package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {

    /**
     * Lists all comments for a post, oldest first.
     *
     * <p>NOTE — must be a NATIVE query, not JPQL. On production we observed
     * that the JPQL {@code WHERE c.post.id = :postId} returns an empty
     * result-set for posts whose rows are visible via the working
     * {@code IN}-batch query {@link #countByPostIdIn}. Bypassing JPQL
     * navigates straight to the {@code post_id} column and fixes the
     * "comments disappear on reload" bug.</p>
     */
    @Query(value = "SELECT * FROM community_comments WHERE post_id = :postId ORDER BY created_at ASC",
           nativeQuery = true)
    List<CommunityComment> findByPostIdOrderByCreatedAtAsc(@Param("postId") UUID postId);

    long countByParent_Id(UUID parentId);

    /** Profile-page metric — total comments authored by a user across all posts. */
    @Query(value = "SELECT COUNT(*) FROM community_comments WHERE user_id = :authorId AND deleted_at IS NULL",
           nativeQuery = true)
    long countActiveByAuthorId(@Param("authorId") UUID authorId);

    /** See note on {@link #findByPostIdOrderByCreatedAtAsc(UUID)} — same JPQL bug, same fix. */
    @Query(value = "SELECT COUNT(*) FROM community_comments WHERE post_id = :postId",
           nativeQuery = true)
    long countByPost_Id(@Param("postId") UUID postId);

    /**
     * Batch comment count for a set of post IDs — avoids N+1 in list views.
     *
     * <p>Native SQL for the same reason {@link #findByPostIdOrderByCreatedAtAsc}
     * is native: in production the JPQL form ({@code WHERE c.post.id IN ...})
     * silently returned an empty result-set, so the listing fell back to
     * the stale denormalized {@code community_posts.comments_count} and the
     * listing badge drifted out of sync with the actual rows.</p>
     */
    @Query(value = "SELECT post_id, COUNT(*) FROM community_comments WHERE post_id IN (:postIds) GROUP BY post_id",
           nativeQuery = true)
    List<Object[]> countByPostIdIn(@Param("postIds") Collection<UUID> postIds);

    /** Admin moderation list — newest first */
    org.springframework.data.domain.Page<CommunityComment> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);

    /**
     * Looks up the most recent comment this user authored on the given
     * post inside the last {@code sinceSeconds}. Used by the soft
     * duplicate-comment guard so we can reject "spam Enter" submissions
     * where the body is identical and the timestamp is seconds apart.
     */
    @Query(value = "SELECT * FROM community_comments " +
                   "WHERE post_id = :postId AND user_id = :userId " +
                   "  AND created_at > NOW() - make_interval(secs => :sinceSeconds) " +
                   "ORDER BY created_at DESC LIMIT 1",
           nativeQuery = true)
    java.util.Optional<CommunityComment> findMostRecentByAuthorOnPost(
            @Param("postId") UUID postId,
            @Param("userId") UUID userId,
            @Param("sinceSeconds") int sinceSeconds);
}
