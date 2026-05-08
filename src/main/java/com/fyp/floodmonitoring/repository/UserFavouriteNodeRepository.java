package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.UserFavouriteNode;
import com.fyp.floodmonitoring.entity.UserFavouriteNodeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFavouriteNodeRepository extends JpaRepository<UserFavouriteNode, UserFavouriteNodeId> {

    List<UserFavouriteNode> findByIdUserId(UUID userId);

    boolean existsByIdUserIdAndIdNodeId(UUID userId, UUID nodeId);

    void deleteByIdUserId(UUID userId);

    /**
     * Look up a favourite by the alerting node's BUSINESS nodeId (the string
     * key shared with the IoT firmware), not the internal UUID. Used by the
     * notification dispatcher to honour per-favourite channel toggles.
     */
    @Query("""
        SELECT f FROM UserFavouriteNode f
        WHERE f.id.userId = :userId
          AND f.id.nodeId = (SELECT n.id FROM Node n WHERE n.nodeId = :nodeIdStr)
        """)
    Optional<UserFavouriteNode> findByUserIdAndBusinessNodeId(
            @Param("userId") UUID userId,
            @Param("nodeIdStr") String nodeIdStr);
}
