package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.CreateSavedLocationRequest;
import com.fyp.floodmonitoring.dto.SavedLocationDto;
import com.fyp.floodmonitoring.dto.UpdateSavedLocationRequest;
import com.fyp.floodmonitoring.entity.UserSavedLocation;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.UserSavedLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for user_saved_locations. The radius validator is layered: the
 * DB has a CHECK (1.0–50.0), the DTO has @DecimalMin / @DecimalMax, and
 * here we double-check before save so a malicious client can't bypass
 * Bean Validation through a hand-crafted request.
 *
 * Soft cap: 25 saved locations per user — keeps the radius-aware
 * notification fan-out's Haversine query bounded.
 */
@Service
@RequiredArgsConstructor
public class SavedLocationService {

    private static final int MAX_PINS_PER_USER = 25;
    private static final BigDecimal MIN_RADIUS = new BigDecimal("1.0");
    private static final BigDecimal MAX_RADIUS = new BigDecimal("50.0");

    private final UserSavedLocationRepository repo;

    @Transactional(readOnly = true)
    public List<SavedLocationDto> listForUser(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SavedLocationDto create(UUID userId, CreateSavedLocationRequest req) {
        long existing = repo.countByUserId(userId);
        if (existing >= MAX_PINS_PER_USER) {
            throw AppException.badRequest("PIN_LIMIT",
                    "You can save up to " + MAX_PINS_PER_USER + " locations.");
        }
        BigDecimal radius = clampRadius(req.alertRadiusKm());
        UserSavedLocation saved = repo.save(UserSavedLocation.builder()
                .userId(userId)
                .label(req.label().trim())
                .address(req.address() == null ? null : req.address().trim())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .alertRadiusKm(radius)
                .build());
        return toDto(saved);
    }

    @Transactional
    public SavedLocationDto update(UUID userId, UUID id, UpdateSavedLocationRequest req) {
        UserSavedLocation pin = repo.findById(id)
                .orElseThrow(() -> AppException.notFound("Saved location not found"));
        if (!pin.getUserId().equals(userId)) {
            throw AppException.forbidden("Not your saved location.");
        }
        if (req.label()     != null) pin.setLabel(req.label().trim());
        if (req.address()   != null) pin.setAddress(req.address().trim());
        if (req.latitude()  != null) pin.setLatitude(req.latitude());
        if (req.longitude() != null) pin.setLongitude(req.longitude());
        if (req.alertRadiusKm() != null) pin.setAlertRadiusKm(clampRadius(req.alertRadiusKm()));
        pin.setUpdatedAt(Instant.now());
        return toDto(pin);  // dirty-checked, persisted on commit
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        UserSavedLocation pin = repo.findById(id)
                .orElseThrow(() -> AppException.notFound("Saved location not found"));
        if (!pin.getUserId().equals(userId)) {
            throw AppException.forbidden("Not your saved location.");
        }
        repo.delete(pin);
    }

    private BigDecimal clampRadius(Double raw) {
        if (raw == null) return new BigDecimal("5.0");
        BigDecimal v = BigDecimal.valueOf(raw).setScale(2, java.math.RoundingMode.HALF_UP);
        if (v.compareTo(MIN_RADIUS) < 0) v = MIN_RADIUS;
        if (v.compareTo(MAX_RADIUS) > 0) v = MAX_RADIUS;
        return v;
    }

    private SavedLocationDto toDto(UserSavedLocation p) {
        return new SavedLocationDto(
                p.getId().toString(),
                p.getLabel(),
                p.getAddress(),
                p.getLatitude(),
                p.getLongitude(),
                p.getAlertRadiusKm().doubleValue(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
