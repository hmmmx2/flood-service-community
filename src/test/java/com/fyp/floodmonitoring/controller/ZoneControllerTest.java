package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.dto.response.ZoneDto;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.ZoneService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link ZoneController}.
 *
 * <p>Both zone endpoints require authentication (not public). They return flood
 * risk zone data with GeoJSON polygon boundaries.</p>
 */
@WebMvcTest(
        controllers = ZoneController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@Import(TestSecurityConfig.class)
@DisplayName("ZoneController Tests")
class ZoneControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private ZoneService zoneService;

    private ZoneDto sampleZone;

    @BeforeEach
    void setUp() {
        sampleZone = TestDataBuilder.buildZoneDto();
    }

    // ── GET /zones ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /zones")
    class GetAllZones {

        @Test
        @WithMockUser(username = "user-id")
        @DisplayName("returns 200 with zone list for authenticated user")
        void getAll_Authenticated_Returns200() throws Exception {
            when(zoneService.getAll()).thenReturn(List.of(sampleZone));

            mockMvc.perform(get("/zones"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value("00000000-0000-0000-0000-000000000060"))
                    .andExpect(jsonPath("$[0].name").value("Kuching River Zone"))
                    .andExpect(jsonPath("$[0].riskLevel").value("high"))
                    .andExpect(jsonPath("$[0].boundary").exists())
                    .andExpect(jsonPath("$[0].updatedAt").exists());
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getAll_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/zones"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "user-id")
        @DisplayName("returns 200 with empty list when no zones defined")
        void getAll_Empty_Returns200() throws Exception {
            when(zoneService.getAll()).thenReturn(List.of());

            mockMvc.perform(get("/zones"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @WithMockUser(username = "user-id")
        @DisplayName("returns zones ordered by risk level descending")
        void getAll_MultipleZones_OrderedByRisk() throws Exception {
            ZoneDto lowZone = new ZoneDto("zone-2", "Low Risk Area", "low",
                    "{\"type\":\"Polygon\"}", "2025-06-01T00:00:00Z");
            ZoneDto extremeZone = new ZoneDto("zone-3", "Extreme Risk Area", "extreme",
                    "{\"type\":\"Polygon\"}", "2025-06-01T00:00:00Z");

            when(zoneService.getAll()).thenReturn(List.of(extremeZone, sampleZone, lowZone));

            mockMvc.perform(get("/zones"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].riskLevel").value("extreme"))
                    .andExpect(jsonPath("$[1].riskLevel").value("high"))
                    .andExpect(jsonPath("$[2].riskLevel").value("low"));
        }
    }

    // ── GET /zones/{id} ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /zones/{id}")
    class GetZoneById {

        @Test
        @WithMockUser(username = "user-id")
        @DisplayName("returns 200 with zone data when found")
        void getById_Found_Returns200() throws Exception {
            UUID id = UUID.fromString("00000000-0000-0000-0000-000000000060");
            when(zoneService.getById(id)).thenReturn(sampleZone);

            mockMvc.perform(get("/zones/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000060"))
                    .andExpect(jsonPath("$.name").value("Kuching River Zone"))
                    .andExpect(jsonPath("$.riskLevel").value("high"))
                    .andExpect(jsonPath("$.boundary").isString());
        }

        @Test
        @WithMockUser(username = "user-id")
        @DisplayName("returns 404 when zone not found")
        void getById_NotFound_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(zoneService.getById(any(UUID.class)))
                    .thenThrow(AppException.notFound("Zone not found: " + id));

            mockMvc.perform(get("/zones/" + id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getById_Unauthenticated_Returns401() throws Exception {
            UUID id = UUID.fromString("00000000-0000-0000-0000-000000000060");

            mockMvc.perform(get("/zones/" + id))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "user-id")
        @DisplayName("boundary field contains valid GeoJSON string")
        void getById_BoundaryIsGeoJson() throws Exception {
            UUID id = UUID.fromString("00000000-0000-0000-0000-000000000060");
            when(zoneService.getById(id)).thenReturn(sampleZone);

            mockMvc.perform(get("/zones/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.boundary").value(org.hamcrest.Matchers.containsString("Polygon")));
        }
    }
}
