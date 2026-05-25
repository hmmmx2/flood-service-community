package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, CommunityPostLike.LikeId> {

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);

    /** Clear every like on a post — used by the post-delete cascade. */
    @Modifying
    @Query("DELETE FROM CommunityPostLike l WHERE l.postId = :postId")
    void deleteByPostId(@Param("postId") UUID postId);

    @Query("SELECT l.postId FROM CommunityPostLike l WHERE l.userId = :userId")
    List<UUID> findPostIdByUserId(@Param("userId") UUID userId);
}
