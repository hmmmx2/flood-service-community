package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.UpdateSettingRequest;
import com.fyp.floodmonitoring.dto.response.SettingsDto;
import com.fyp.floodmonitoring.entity.UserSetting;
import com.fyp.floodmonitoring.repository.UserRepository;
import com.fyp.floodmonitoring.repository.UserSettingRepository;
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

    @Transactional(readOnly = true)
    public SettingsDto getSettings(UUID userId) {
        return buildDto(userId);
    }

    @Transactional
    public SettingsDto updateSetting(UUID userId, UpdateSettingRequest req) {
        UserSetting setting = settingRepository.findByUserIdAndKey(userId, req.key())
                .orElseGet(() -> UserSetting.builder()
                        .userId(userId)
                        .key(req.key())
                        .build());

        setting.setEnabled(req.enabled());
        settingRepository.save(setting);

        return buildDto(userId);
    }

    @Transactional
    public void registerPushToken(UUID userId, String pushToken) {
        userRepository.updatePushToken(userId, pushToken);
    }

    private SettingsDto buildDto(UUID userId) {
        List<SettingsDto.SettingItemDto> items = settingRepository
                .findByUserIdOrderByKeyAsc(userId)
                .stream()
                .map(s -> new SettingsDto.SettingItemDto(s.getKey(),
                        Boolean.TRUE.equals(s.getEnabled())))
                .toList();
        return new SettingsDto(userId.toString(), items);
    }
}
