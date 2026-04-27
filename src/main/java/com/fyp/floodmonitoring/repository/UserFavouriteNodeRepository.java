package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.UserFavouriteNode;
import com.fyp.floodmonitoring.entity.UserFavouriteNodeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserFavouriteNodeRepository extends JpaRepository<UserFavouriteNode, UserFavouriteNodeId> {

    List<UserFavouriteNode> findByIdUserId(UUID userId);

    boolean existsByIdUserIdAndIdNodeId(UUID userId, UUID nodeId);
}
