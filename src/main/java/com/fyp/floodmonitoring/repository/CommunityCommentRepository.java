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

    /** See note on {@link #findByPostIdOrderByCreatedAtAsc(UUID)} — same JPQL bug, same fix. */
    @Query(value = "SELECT COUNT(*) FROM community_comments WHERE post_id = :postId",
           nativeQuery = true)
    long countByPost_Id(@Param("postId") UUID postId);

    /** Batch comment count for a set of post IDs — avoids N+1 in list views. */
    @Query("SELECT c.post.id, COUNT(c) FROM CommunityComment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIdIn(@Param("postIds") Collection<UUID> postIds);

    /** Admin moderation list — newest first */
    org.springframework.data.domain.Page<CommunityComment> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);
}
