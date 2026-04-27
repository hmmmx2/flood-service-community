package com.fyp.floodmonitoring.dto.response;

import java.util.List;

public record SettingsDto(String userId, List<SettingItemDto> items) {

    public record SettingItemDto(String key, boolean enabled) {}
}
