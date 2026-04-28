package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.dto.response.DashboardNodeRowDto;
import com.fyp.floodmonitoring.dto.response.DashboardTimeSeriesDto;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.DashboardService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link DashboardController}.
 *
 * <p>The dashboard is protected by a class-level {@code @PreAuthorize} that requires
 * one of ADMIN, OPERATIONS_MANAGER, FIELD_TECHNICIAN, or VIEWER roles.
 * Tests cover both authorised (admin) and unauthorised (customer) scenarios.</p>
 */
@WebMvcTest(
        controllers = DashboardController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@Import(TestSecurityConfig.class)
@DisplayName("DashboardController Tests")
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private DashboardService dashboardService;

    private DashboardNodeRowDto sampleRow;
    private DashboardTimeSeriesDto sampleTimeSeries;

    @BeforeEach
    void setUp() {
        sampleRow       = TestDataBuilder.buildDashboardRow();
        sampleTimeSeries = TestDataBuilder.buildTimeSeries();
    }

    // ── GET /dashboard/nodes ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /dashboard/nodes")
    class GetDashboardNodes {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 200 with node list for admin role")
        void getNodes_Admin_Returns200() throws Exception {
            when(dashboardService.getDashboardNodes()).thenReturn(List.of(sampleRow));

            mockMvc.perform(get("/dashboard/nodes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value("00000000-0000-0000-0000-000000000050"))
                    .andExpect(jsonPath("$[0].area").value("Kuching"))
                    .andExpect(jsonPath("$[0].status").value("Normal"))
                    .andExpect(jsonPath("$[0].state").value("Sarawak"));
        }

        @Test
        @WithMockUser(roles = "OPERATIONS_MANAGER")
        @DisplayName("returns 200 for operations manager role")
        void getNodes_OperationsManager_Returns200() throws Exception {
            when(dashboardService.getDashboardNodes()).thenReturn(List.of(sampleRow));

            mockMvc.perform(get("/dashboard/nodes"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("returns 403 for customer role")
        void getNodes_Customer_Returns403() throws Exception {
            mockMvc.perform(get("/dashboard/nodes"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getNodes_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/dashboard/nodes"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 200 with empty list when no nodes")
        void getNodes_EmptyList_Returns200() throws Exception {
            when(dashboardService.getDashboardNodes()).thenReturn(List.of());

            mockMvc.perform(get("/dashboard/nodes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 200 with multiple nodes ordered by level")
        void getNodes_MultipleNodes_Returns200() throws Exception {
            DashboardNodeRowDto criticalRow = new DashboardNodeRowDto(
                    "00000000-0000-0000-0000-000000000052",
                    "3", "Kuching", "Sungai Sarawak", "Sarawak", "Critical", "1m ago", "09:00 AM"
            );
            when(dashboardService.getDashboardNodes()).thenReturn(List.of(criticalRow, sampleRow));

            mockMvc.perform(get("/dashboard/nodes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].status").value("Critical"))
                    .andExpect(jsonPath("$[1].status").value("Normal"));
        }
    }

    // ── GET /dashboard/time-series ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /dashboard/time-series")
    class GetTimeSeries {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 200 with monthly and yearly series for admin")
        void getTimeSeries_Admin_Returns200() throws Exception {
            when(dashboardService.getTimeSeries()).thenReturn(sampleTimeSeries);

            mockMvc.perform(get("/dashboard/time-series"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.monthly").isArray())
                    .andExpect(jsonPath("$.yearly").isArray())
                    .andExpect(jsonPath("$.monthly.length()").value(12))
                    .andExpect(jsonPath("$.yearly.length()").value(5));
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("returns 200 for viewer role")
        void getTimeSeries_Viewer_Returns200() throws Exception {
            when(dashboardService.getTimeSeries()).thenReturn(sampleTimeSeries);

            mockMvc.perform(get("/dashboard/time-series"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("returns 403 for customer role")
        void getTimeSeries_Customer_Returns403() throws Exception {
            mockMvc.perform(get("/dashboard/time-series"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getTimeSeries_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/dashboard/time-series"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 200 with all-zero series when no events recorded")
        void getTimeSeries_AllZeros_Returns200() throws Exception {
            DashboardTimeSeriesDto zeros = new DashboardTimeSeriesDto(
                    List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                    List.of(0, 0, 0, 0, 0)
            );
            when(dashboardService.getTimeSeries()).thenReturn(zeros);

            mockMvc.perform(get("/dashboard/time-series"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.monthly[0]").value(0))
                    .andExpect(jsonPath("$.yearly[0]").value(0));
        }

        @Test
        @WithMockUser(roles = "FIELD_TECHNICIAN")
        @DisplayName("returns 200 for field technician role")
        void getTimeSeries_FieldTechnician_Returns200() throws Exception {
            when(dashboardService.getTimeSeries()).thenReturn(sampleTimeSeries);

            mockMvc.perform(get("/dashboard/time-series"))
                    .andExpect(status().isOk());
        }
    }
}
