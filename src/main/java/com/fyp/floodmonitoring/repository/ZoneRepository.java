package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    List<Zone> findAllByOrderByRiskLevelDescNameAsc();
}
