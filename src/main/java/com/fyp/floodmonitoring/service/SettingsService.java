package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.UpdateSettingRequest;
import com.fyp.floodmonitoring.dto.request.WebPushSubscriptionRequest;
import com.fyp.floodmonitoring.dto.response.SettingsDto;
import com.fyp.floodmonitoring.entity.UserSetting;
import com.fyp.floodmonitoring.entity.WebPushSubscription;
import com.fyp.floodmonitoring.repository.UserRepository;
import com.fyp.floodmonitoring.repository.UserSettingRepository;
import com.fyp.floodmonitoring.repository.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final WebPushSubscriptionRepository webPushRepository;

    @Transactional(readOnly = true)
    public List<SettingsDto.SettingItemDto> getSettings(UUID userId) {
        return buildItems(userId);
    }

    @Transactional
    public List<SettingsDto.SettingItemDto> updateSetting(UUID userId, UpdateSettingRequest req) {
        UserSetting setting = settingRepository.findByUserIdAndKey(userId, req.key())
                .orElseGet(() -> UserSetting.builder()
                        .userId(userId)
                        .key(req.key())
                        .build());

        setting.setEnabled(req.enabled());
        settingRepository.save(setting);

        return buildItems(userId);
    }

    @Transactional
    public void registerPushToken(UUID userId, String pushToken) {
        userRepository.updatePushToken(userId, pushToken);
    }

    @Transactional
    public void saveWebPushSubscription(UUID userId, WebPushSubscriptionRequest req) {
        if (webPushRepository.existsByEndpoint(req.endpoint())) {
            return; // already registered — idempotent
        }
        WebPushSubscription sub = WebPushSubscription.builder()
                .userId(userId)
                .endpoint(req.endpoint())
                .p256dh(req.keys().p256dh())
                .authKey(req.keys().auth())
                .build();
        webPushRepository.save(sub);
    }

    @Transactional
    public void removeWebPushSubscription(UUID userId, String endpoint) {
        webPushRepository.deleteByEndpointAndUserId(endpoint, userId);
    }

    private List<SettingsDto.SettingItemDto> buildItems(UUID userId) {
        return settingRepository
                .findByUserIdOrderByKeyAsc(userId)
                .stream()
                .map(s -> new SettingsDto.SettingItemDto(s.getKey(),
                        Boolean.TRUE.equals(s.getEnabled())))
                .toList();
    }
}
