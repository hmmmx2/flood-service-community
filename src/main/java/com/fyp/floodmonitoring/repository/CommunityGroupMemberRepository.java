package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.CommunityGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommunityGroupMemberRepository extends JpaRepository<CommunityGroupMember, CommunityGroupMember.MemberId> {

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);

    @Query("SELECT m.groupId FROM CommunityGroupMember m WHERE m.userId = :userId")
    List<UUID> findGroupIdByUserId(@Param("userId") UUID userId);

    /** Used by the new-post fan-out — every member receives a notification when someone posts in their group. */
    @Query("SELECT m.userId FROM CommunityGroupMember m WHERE m.groupId = :groupId")
    List<UUID> findUserIdByGroupId(@Param("groupId") UUID groupId);
}
