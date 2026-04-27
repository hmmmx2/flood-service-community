package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.Broadcast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BroadcastRepository extends JpaRepository<Broadcast, UUID> {

    List<Broadcast> findAllByOrderBySentAtDesc();
}
