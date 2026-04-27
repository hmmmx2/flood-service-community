package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.SafetyContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SafetyContentRepository extends JpaRepository<SafetyContent, UUID> {

    List<SafetyContent> findByLangOrderBySectionAsc(String lang);
}
