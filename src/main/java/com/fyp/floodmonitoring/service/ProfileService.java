package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.UpdateProfileRequest;
import com.fyp.floodmonitoring.dto.response.UserProfileDto;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;

    // ── GET ───────────────────────────────────────────────────────────────────

    public UserProfileDto getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));
        return toDto(user);
    }

    // ── PATCH ─────────────────────────────────────────────────────────────────

    @Transactional
    public UserProfileDto updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        // Apply partial updates — only set fields that are present (non-null) in the request
        if (StringUtils.hasText(req.firstName())) {
            user.setFirstName(req.firstName().trim());
        }
        if (req.lastName() != null) {
            user.setLastName(req.lastName().trim());
        }
        if (req.phone() != null) {
            user.setPhone(req.phone().isBlank() ? null : req.phone().trim());
        }
        if (req.locationLabel() != null) {
            user.setLocationLabel(req.locationLabel().isBlank() ? null : req.locationLabel().trim());
        }

        user = userRepository.save(user);
        return toDto(user);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private UserProfileDto toDto(User u) {
        String displayName = (u.getFirstName() + " " + u.getLastName()).trim();
        return new UserProfileDto(
                u.getId().toString(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                displayName,
                u.getRole(),
                u.getPhone(),
                u.getLocationLabel(),
                u.getAvatarUrl());
    }
}
