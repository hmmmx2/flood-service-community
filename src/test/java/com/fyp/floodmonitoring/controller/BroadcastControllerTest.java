package com.fyp.floodmonitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.request.CreateBroadcastRequest;
import com.fyp.floodmonitoring.dto.response.BroadcastDto;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.BroadcastService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = BroadcastController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@Import(TestSecurityConfig.class)
@DisplayName("BroadcastController Tests")
class BroadcastControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private BroadcastService broadcastService;

    private BroadcastDto sampleBroadcast;

    @BeforeEach
    void setUp() {
        sampleBroadcast = new BroadcastDto(
            "broadcast-uuid-001",
            "Flood Warning - Sungai Sarawak",
            "Water levels are rising. Please evacuate low-lying areas.",
            "Zone A",
            "warning",
            "admin-uuid-1234",
            "2025-01-01T08:00:00Z",
            245
        );
    }

    @Nested
    @DisplayName("GET /broadcasts")
    class GetBroadcasts {

        @Test
        @WithMockUser
        @DisplayName("returns 200 with all broadcasts")
        void getBroadcasts_Returns200() throws Exception {
            when(broadcastService.getAll()).thenReturn(List.of(sampleBroadcast));

            mockMvc.perform(get("/broadcasts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("broadcast-uuid-001"))
                .andExpect(jsonPath("$[0].title").value("Flood Warning - Sungai Sarawak"))
                .andExpect(jsonPath("$[0].severity").value("warning"))
                .andExpect(jsonPath("$[0].recipientCount").value(245));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with empty list when no broadcasts")
        void getBroadcasts_Empty_Returns200() throws Exception {
            when(broadcastService.getAll()).thenReturn(List.of());

            mockMvc.perform(get("/broadcasts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getBroadcasts_NoAuth_Returns401() throws Exception {
            mockMvc.perform(get("/broadcasts"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /broadcasts")
    class CreateBroadcast {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
        @DisplayName("returns 201 with created broadcast for admin")
        void createBroadcast_Admin_Returns201() throws Exception {
            when(broadcastService.create(any(), any())).thenReturn(sampleBroadcast);

            CreateBroadcastRequest req = new CreateBroadcastRequest(
                "Flood Warning - Sungai Sarawak",
                "Water levels are rising. Please evacuate low-lying areas.",
                "Zone A", "warning"
            );

            mockMvc.perform(post("/broadcasts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("broadcast-uuid-001"))
                .andExpect(jsonPath("$.targetZone").value("Zone A"))
                .andExpect(jsonPath("$.severity").value("warning"));
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("returns 403 for non-admin user")
        void createBroadcast_Customer_Returns403() throws Exception {
            CreateBroadcastRequest req = new CreateBroadcastRequest("Title", "Body", "Zone A", null);

            mockMvc.perform(post("/broadcasts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 400 when title is blank")
        void createBroadcast_BlankTitle_Returns400() throws Exception {
            CreateBroadcastRequest req = new CreateBroadcastRequest("", "Body", "Zone A", null);

            mockMvc.perform(post("/broadcasts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 400 when target zone is blank")
        void createBroadcast_BlankTargetZone_Returns400() throws Exception {
            CreateBroadcastRequest req = new CreateBroadcastRequest("Title", "Body", "", null);

            mockMvc.perform(post("/broadcasts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 for unauthenticated request")
        void createBroadcast_NoAuth_Returns401() throws Exception {
            CreateBroadcastRequest req = new CreateBroadcastRequest("Title", "Body", "Zone A", null);

            mockMvc.perform(post("/broadcasts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
        }
    }
}
