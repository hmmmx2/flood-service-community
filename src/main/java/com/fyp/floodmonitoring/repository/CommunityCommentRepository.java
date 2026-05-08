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
     * Lists all comments for a post, oldest first. Uses an explicit JPQL
     * with the `c.post.id` path (proven to compile + match the same
     * syntax {@link #countByPostIdIn} uses), instead of the Spring Data
     * derived-method name {@code findByPost_IdOrderByCreatedAtAsc} which
     * was returning empty result-sets on production for some posts.
     */
    @Query("SELECT c FROM CommunityComment c WHERE c.post.id = :postId ORDER BY c.createdAt ASC")
    List<CommunityComment> findByPostIdOrderByCreatedAtAsc(@Param("postId") UUID postId);

    long countByParent_Id(UUID parentId);

    @Query("SELECT COUNT(c) FROM CommunityComment c WHERE c.post.id = :postId")
    long countByPost_Id(@Param("postId") UUID postId);

    /** Batch comment count for a set of post IDs — avoids N+1 in list views. */
    @Query("SELECT c.post.id, COUNT(c) FROM CommunityComment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIdIn(@Param("postIds") Collection<UUID> postIds);

    /** Admin moderation list — newest first */
    org.springframework.data.domain.Page<CommunityComment> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);
}
