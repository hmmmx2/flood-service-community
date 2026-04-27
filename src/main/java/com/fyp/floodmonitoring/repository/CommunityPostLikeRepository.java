package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, CommunityPostLike.LikeId> {

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);

    @Query("SELECT l.postId FROM CommunityPostLike l WHERE l.userId = :userId")
    List<UUID> findPostIdByUserId(@Param("userId") UUID userId);
}
