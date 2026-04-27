package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.Node;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NodeRepository extends JpaRepository<Node, UUID> {

    List<Node> findAllByOrderByNodeIdAsc();

    List<Node> findAllByOrderByCurrentLevelDescNodeIdAsc();

    long countByIsDeadFalse();

    java.util.Optional<Node> findByNodeId(String nodeId);
}
