package com.fyp.floodmonitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.dto.request.RegisterPushTokenRequest;
import com.fyp.floodmonitoring.dto.request.UpdateSettingRequest;
import com.fyp.floodmonitoring.dto.response.SettingsDto;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.SettingsService;
import com.fyp.floodmonitoring.util.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link SettingsController}.
 *
 * <p>All three endpoints require authentication. The push-token endpoint
 * delegates to {@code settingsService.registerPushToken()} and returns 204.</p>
 */
@WebMvcTest(
        controllers = SettingsController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@Import(TestSecurityConfig.class)
@DisplayName("SettingsController Tests")
class SettingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private SettingsService settingsService;

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private List<SettingsDto.SettingItemDto> defaultSettings;

    @BeforeEach
    void setUp() {
        defaultSettings = TestDataBuilder.buildDefaultSettings();
    }

    // ── GET /settings ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /settings")
    class GetSettings {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 200 with settings list for authenticated user")
        void getSettings_Authenticated_Returns200() throws Exception {
            when(settingsService.getSettings(UUID.fromString(USER_ID))).thenReturn(defaultSettings);

            mockMvc.perform(get("/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0].key").value("emailNotifications"))
                    .andExpect(jsonPath("$[0].enabled").value(false))
                    .andExpect(jsonPath("$[2].key").value("pushNotifications"));
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getSettings_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/settings"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 200 with empty list when no settings configured")
        void getSettings_NoSettings_Returns200Empty() throws Exception {
            when(settingsService.getSettings(any(UUID.class))).thenReturn(List.of());

            mockMvc.perform(get("/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ── PATCH /settings ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /settings")
    class UpdateSetting {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 200 with updated settings list on valid request")
        void updateSetting_Valid_Returns200() throws Exception {
            List<SettingsDto.SettingItemDto> updated = List.of(
                    new SettingsDto.SettingItemDto("emailNotifications", false),
                    new SettingsDto.SettingItemDto("lowDataMode", false),
                    new SettingsDto.SettingItemDto("pushNotifications", true),
                    new SettingsDto.SettingItemDto("smsNotifications", false)
            );
            when(settingsService.updateSetting(eq(UUID.fromString(USER_ID)), any(UpdateSettingRequest.class)))
                    .thenReturn(updated);

            UpdateSettingRequest req = new UpdateSettingRequest("pushNotifications", true);

            mockMvc.perform(patch("/settings")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[2].key").value("pushNotifications"))
                    .andExpect(jsonPath("$[2].enabled").value(true));
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 400 when key is an invalid setting name")
        void updateSetting_InvalidKey_Returns400() throws Exception {
            UpdateSettingRequest req = new UpdateSettingRequest("invalidKey", true);

            mockMvc.perform(patch("/settings")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 400 when key is blank")
        void updateSetting_BlankKey_Returns400() throws Exception {
            mockMvc.perform(patch("/settings")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"\",\"enabled\":true}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 400 when enabled field is null")
        void updateSetting_NullEnabled_Returns400() throws Exception {
            mockMvc.perform(patch("/settings")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"key\":\"pushNotifications\",\"enabled\":null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void updateSetting_Unauthenticated_Returns401() throws Exception {
            UpdateSettingRequest req = new UpdateSettingRequest("pushNotifications", true);

            mockMvc.perform(patch("/settings")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── PATCH /settings/push-token ─────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /settings/push-token")
    class RegisterPushToken {

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 204 on successful push-token registration")
        void registerPushToken_Valid_Returns204() throws Exception {
            doNothing().when(settingsService).registerPushToken(any(UUID.class), eq("ExponentPushToken[test123]"));

            RegisterPushTokenRequest req = new RegisterPushTokenRequest("ExponentPushToken[test123]");

            mockMvc.perform(patch("/settings/push-token")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNoContent());

            verify(settingsService).registerPushToken(eq(UUID.fromString(USER_ID)), eq("ExponentPushToken[test123]"));
        }

        @Test
        @WithMockUser(username = USER_ID)
        @DisplayName("returns 400 when token is blank")
        void registerPushToken_BlankToken_Returns400() throws Exception {
            RegisterPushTokenRequest req = new RegisterPushTokenRequest("");

            mockMvc.perform(patch("/settings/push-token")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void registerPushToken_Unauthenticated_Returns401() throws Exception {
            RegisterPushTokenRequest req = new RegisterPushTokenRequest("ExponentPushToken[test123]");

            mockMvc.perform(patch("/settings/push-token")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
