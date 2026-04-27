package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.SafetyContentDto;
import com.fyp.floodmonitoring.repository.SafetyContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Retrieves safety awareness content, cached for 60 minutes (SCRUM-103).
 * Cache lives in SafetyService (not the controller) so only plain DTOs are stored.
 */
@Service
@RequiredArgsConstructor
public class SafetyService {

    private final SafetyContentRepository safetyRepo;

    @Cacheable(value = "safety", key = "#lang")
    public List<SafetyContentDto> getSafety(String lang) {
        return safetyRepo.findByLangOrderBySectionAsc(lang)
                .stream()
                .map(s -> new SafetyContentDto(
                        s.getSection(),
                        s.getContent(),
                        s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null))
                .toList();
    }
}
