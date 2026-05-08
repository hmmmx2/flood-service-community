package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.NotificationPrefsDto;
import com.fyp.floodmonitoring.dto.UpdateNotificationPrefsRequest;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.entity.UserSetting;
import com.fyp.floodmonitoring.repository.UserRepository;
import com.fyp.floodmonitoring.repository.UserSettingRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * GET /profile/notification-prefs   — current channel prefs + phone
 * PATCH /profile/notification-prefs — partial update
 *
 * Channel keys live in user_settings:
 *   • emailAlerts   — opt in to email
 *   • smsAlerts     — opt in to SMS  (requires phoneE164)
 *   • whatsappAlerts — opt in to WhatsApp (requires phoneE164)
 *   • inAppAlerts   — opt in to bell-dropdown notifications
 *
 * Phone is stored on the user row (already exists as `phone` — we
 * treat it as E.164 going forward; the new validator rejects anything
 * else).
 */
@RestController
@RequestMapping("/profile/notification-prefs")
@RequiredArgsConstructor
public class NotificationPrefsController {

    private final UserRepository        userRepository;
    private final UserSettingRepository userSettingRepository;

    @GetMapping
    public ResponseEntity<NotificationPrefsDto> get(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        User u = userRepository.findById(userId).orElseThrow();
        return ResponseEntity.ok(buildDto(u));
    }

    @PatchMapping
    public ResponseEntity<NotificationPrefsDto> update(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateNotificationPrefsRequest req) {
        UUID userId = UUID.fromString(principal.getUsername());
        User u = userRepository.findById(userId).orElseThrow();

        if (req.phoneE164() != null) {
            u.setPhone(req.phoneE164().isBlank() ? null : req.phoneE164().trim());
            userRepository.save(u);
        }
        if (req.notifyEmail()    != null) upsertSetting(userId, "emailAlerts",     req.notifyEmail());
        if (req.notifySms()      != null) upsertSetting(userId, "smsAlerts",       req.notifySms());
        if (req.notifyWhatsapp() != null) upsertSetting(userId, "whatsappAlerts",  req.notifyWhatsapp());
        if (req.notifyInApp()    != null) upsertSetting(userId, "inAppAlerts",     req.notifyInApp());

        return ResponseEntity.ok(buildDto(userRepository.findById(userId).orElseThrow()));
    }

    private void upsertSetting(UUID userId, String key, boolean enabled) {
        UserSetting s = userSettingRepository.findByUserIdAndKey(userId, key)
                .orElseGet(() -> UserSetting.builder().userId(userId).key(key).enabled(false).build());
        s.setEnabled(enabled);
        userSettingRepository.save(s);
    }

    private NotificationPrefsDto buildDto(User u) {
        boolean email    = userSettingRepository.findByUserIdAndKey(u.getId(), "emailAlerts")
                .map(UserSetting::getEnabled).orElse(false);
        boolean sms      = userSettingRepository.findByUserIdAndKey(u.getId(), "smsAlerts")
                .map(UserSetting::getEnabled).orElse(false);
        boolean whatsapp = userSettingRepository.findByUserIdAndKey(u.getId(), "whatsappAlerts")
                .map(UserSetting::getEnabled).orElse(false);
        boolean inApp    = userSettingRepository.findByUserIdAndKey(u.getId(), "inAppAlerts")
                .map(UserSetting::getEnabled).orElse(true); // default-on for new users
        return new NotificationPrefsDto(u.getPhone(), email, sms, whatsapp, inApp);
    }
}
