package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {

    List<CommunityComment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    Optional<CommunityComment> findByIdAndAuthorId(UUID id, UUID authorId);
}
