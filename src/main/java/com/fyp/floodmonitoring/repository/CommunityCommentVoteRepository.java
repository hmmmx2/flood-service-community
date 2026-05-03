package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.CommunityCommentVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityCommentVoteRepository extends JpaRepository<CommunityCommentVote, UUID> {

    Optional<CommunityCommentVote> findByComment_IdAndUser_Id(UUID commentId, UUID userId);

    void deleteByComment_IdAndUser_Id(UUID commentId, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CommunityCommentVote v WHERE v.comment.id = :commentId")
    void deleteByComment_Id(@Param("commentId") UUID commentId);

    @Query("SELECT COALESCE(SUM(v.value), 0) FROM CommunityCommentVote v WHERE v.comment.id = :commentId")
    int sumValueForComment(@Param("commentId") UUID commentId);

    @Query("SELECT v FROM CommunityCommentVote v WHERE v.comment.id IN :ids AND v.user.id = :userId")
    List<CommunityCommentVote> findByComment_IdInAndUser_Id(
            @Param("ids") List<UUID> ids,
            @Param("userId") UUID userId);
}
