package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.CreateAdminUserRequest;
import com.fyp.floodmonitoring.dto.request.UpdateAdminUserRequest;
import com.fyp.floodmonitoring.dto.response.AdminUserDto;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.UserRepository;
import com.fyp.floodmonitoring.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final List<String> DEFAULT_SETTING_KEYS =
            List.of("pushNotifications", "smsNotifications", "emailNotifications", "lowDataMode");

    private final UserRepository userRepository;
    private final UserSettingRepository settingRepository;
    private final PasswordEncoder passwordEncoder;

    // ── List all users ────────────────────────────────────────────────────────

    public List<AdminUserDto> listAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Create user ───────────────────────────────────────────────────────────

    @Transactional
    public AdminUserDto createUser(CreateAdminUserRequest req) {
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw AppException.conflict("A user with this email already exists");
        }
        if (req.password() == null || req.password().length() < 8) {
            throw AppException.badRequest("WEAK_PASSWORD", "Password must be at least 8 characters");
        }

        String role = (req.role() != null && req.role().equalsIgnoreCase("admin")) ? "admin" : "customer";

        User user = User.builder()
                .firstName(req.firstName() != null ? req.firstName().trim() : "")
                .lastName(req.lastName() != null ? req.lastName().trim() : "")
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(role)
                .build();

        user = userRepository.save(user);
        seedDefaultSettings(user.getId());
        return toDto(user);
    }

    // ── Update user ───────────────────────────────────────────────────────────

    @Transactional
    public AdminUserDto updateUser(UUID id, UpdateAdminUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("User not found"));

        if (req.firstName() != null && !req.firstName().isBlank()) {
            user.setFirstName(req.firstName().trim());
        }
        if (req.lastName() != null && !req.lastName().isBlank()) {
            user.setLastName(req.lastName().trim());
        }
        if (req.role() != null && !req.role().isBlank()) {
            String role = req.role().equalsIgnoreCase("admin") ? "admin" : "customer";
            user.setRole(role);
        }

        user = userRepository.save(user);
        return toDto(user);
    }

    // ── Delete user ───────────────────────────────────────────────────────────

    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw AppException.notFound("User not found");
        }
        userRepository.deleteById(id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AdminUserDto toDto(User u) {
        String displayName = (u.getFirstName() + " " + u.getLastName()).trim();
        if (displayName.isEmpty()) displayName = u.getEmail();
        return new AdminUserDto(
                u.getId().toString(),
                displayName,
                u.getEmail(),
                capitalize(u.getRole()),
                "active",
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : null,
                u.getLastLogin() != null ? u.getLastLogin().toString() : null
        );
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private void seedDefaultSettings(UUID userId) {
        for (String key : DEFAULT_SETTING_KEYS) {
            settingRepository.upsertDefault(userId, key);
        }
    }
}
