package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.CommunityGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityGroupRepository extends JpaRepository<CommunityGroup, UUID> {

    Optional<CommunityGroup> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<CommunityGroup> findAllByOrderByMembersCountDesc();

    @Modifying
    @Query("UPDATE CommunityGroup g SET g.membersCount = g.membersCount + :delta WHERE g.id = :id")
    void adjustMembers(@Param("id") UUID id, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE CommunityGroup g SET g.postsCount = g.postsCount + :delta WHERE g.id = :id")
    void adjustPosts(@Param("id") UUID id, @Param("delta") int delta);
}
