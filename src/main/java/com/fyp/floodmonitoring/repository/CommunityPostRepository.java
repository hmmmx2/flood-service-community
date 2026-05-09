package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    // Global feed
    Page<CommunityPost> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<CommunityPost> findAllByOrderByLikesCountDescCreatedAtDesc(Pageable pageable);

    // Group feed
    Page<CommunityPost> findByGroupIdOrderByCreatedAtDesc(UUID groupId, Pageable pageable);
    Page<CommunityPost> findByGroupIdOrderByLikesCountDescCreatedAtDesc(UUID groupId, Pageable pageable);

    // Full-text search (title + content, case-insensitive)
    @Query("SELECT p FROM CommunityPost p WHERE " +
           "LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY p.createdAt DESC")
    Page<CommunityPost> searchByCreatedAtDesc(@Param("q") String query, Pageable pageable);

    @Query("SELECT p FROM CommunityPost p WHERE " +
           "LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "ORDER BY p.likesCount DESC, p.createdAt DESC")
    Page<CommunityPost> searchByLikesDesc(@Param("q") String query, Pageable pageable);

    @Query("SELECT p FROM CommunityPost p WHERE p.group.id = :groupId AND " +
           "(LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY p.createdAt DESC")
    Page<CommunityPost> searchByGroupAndCreatedAtDesc(
            @Param("groupId") UUID groupId, @Param("q") String query, Pageable pageable);

    @Query("SELECT p FROM CommunityPost p WHERE p.group.id = :groupId AND " +
           "(LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY p.likesCount DESC, p.createdAt DESC")
    Page<CommunityPost> searchByGroupAndLikesDesc(
            @Param("groupId") UUID groupId, @Param("q") String query, Pageable pageable);

    /**
     * Never store a negative like count (drift between counter and like rows).
     *
     * <p>{@code flushAutomatically=true} forces Hibernate to flush pending
     * inserts before the bulk UPDATE runs, so a like + a counter bump in
     * the same transaction can't lose the like row when the persistence
     * context gets cleared. {@code clearAutomatically=false} keeps managed
     * entities around so the calling service can still read their fields
     * and proxies after the call.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = "UPDATE community_posts SET likes_count = GREATEST(0, likes_count + :delta) WHERE id = :id",
            nativeQuery = true)
    void adjustLikes(@Param("id") UUID id, @Param("delta") int delta);

    /**
     * Same flush/clear semantics as {@link #adjustLikes}. The previous
     * {@code clearAutomatically=true} variant was silently discarding the
     * unflushed comment INSERT in {@code addComment} — the controller
     * still returned 200 with the in-memory comment object, but the row
     * was never persisted.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("UPDATE CommunityPost p SET p.commentsCount = p.commentsCount + :delta WHERE p.id = :id")
    void adjustComments(@Param("id") UUID id, @Param("delta") int delta);
}
