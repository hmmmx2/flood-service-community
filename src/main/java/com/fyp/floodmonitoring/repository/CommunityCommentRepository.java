package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {

    List<CommunityComment> findByPost_IdOrderByCreatedAtAsc(UUID postId);

    long countByParent_Id(UUID parentId);

    @Query("SELECT COUNT(c) FROM CommunityComment c WHERE c.post.id = :postId")
    long countByPost_Id(@Param("postId") UUID postId);

    /** Admin moderation list — newest first */
    org.springframework.data.domain.Page<CommunityComment> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);
}
