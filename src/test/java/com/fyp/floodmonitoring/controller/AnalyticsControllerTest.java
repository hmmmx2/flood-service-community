package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.dto.response.AnalyticsDataDto;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.AnalyticsService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link AnalyticsController}.
 *
 * <p>Verifies that the analytics endpoint is only accessible to authenticated users
 * and that the response shape contains the required fields.</p>
 */
@WebMvcTest(
        controllers = AnalyticsController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@Import(TestSecurityConfig.class)
@DisplayName("AnalyticsController Tests")
class AnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private AnalyticsService analyticsService;

    private AnalyticsDataDto sampleAnalytics;

    @BeforeEach
    void setUp() {
        sampleAnalytics = TestDataBuilder.buildAnalyticsData();
    }

    // ── GET /analytics ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /analytics")
    class GetAnalytics {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 200 with analytics payload for admin")
        void getAnalytics_Admin_Returns200() throws Exception {
            when(analyticsService.getAnalytics()).thenReturn(sampleAnalytics);

            mockMvc.perform(get("/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats").isArray())
                    .andExpect(jsonPath("$.chartData").isArray())
                    .andExpect(jsonPath("$.yearlyChartData").isArray())
                    .andExpect(jsonPath("$.waterLevelByNode").isArray())
                    .andExpect(jsonPath("$.floodByState").isArray())
                    .andExpect(jsonPath("$.recentEvents").isArray());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("stats array contains expected labels")
        void getAnalytics_StatsLabels_Correct() throws Exception {
            when(analyticsService.getAnalytics()).thenReturn(sampleAnalytics);

            mockMvc.perform(get("/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats[0].label").value("Active Sensors"))
                    .andExpect(jsonPath("$.stats[1].label").value("Alerts (24h)"))
                    .andExpect(jsonPath("$.stats[2].label").value("Data Points"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("chartData contains 7 daily entries")
        void getAnalytics_ChartDataLength_IsCorrect() throws Exception {
            when(analyticsService.getAnalytics()).thenReturn(sampleAnalytics);

            mockMvc.perform(get("/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chartData.length()").value(7))
                    .andExpect(jsonPath("$.yearlyChartData.length()").value(5));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("waterLevelByNode contains nodeId, level, and status")
        void getAnalytics_WaterLevelByNode_HasExpectedFields() throws Exception {
            when(analyticsService.getAnalytics()).thenReturn(sampleAnalytics);

            mockMvc.perform(get("/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.waterLevelByNode[0].nodeId").value("102782478"))
                    .andExpect(jsonPath("$.waterLevelByNode[0].level").value(2))
                    .andExpect(jsonPath("$.waterLevelByNode[0].status").value("warning"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("floodByState contains state and total")
        void getAnalytics_FloodByState_HasExpectedFields() throws Exception {
            when(analyticsService.getAnalytics()).thenReturn(sampleAnalytics);

            mockMvc.perform(get("/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.floodByState[0].state").value("Sarawak"))
                    .andExpect(jsonPath("$.floodByState[0].total").value(5));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("recentEvents contains title, timestamp, and type")
        void getAnalytics_RecentEvents_HasExpectedFields() throws Exception {
            when(analyticsService.getAnalytics()).thenReturn(sampleAnalytics);

            mockMvc.perform(get("/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recentEvents[0].title").exists())
                    .andExpect(jsonPath("$.recentEvents[0].timestamp").exists())
                    .andExpect(jsonPath("$.recentEvents[0].type").value("warning"));
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("returns 200 for authenticated customer — analytics is open to all auth users")
        void getAnalytics_Customer_Returns200() throws Exception {
            when(analyticsService.getAnalytics()).thenReturn(sampleAnalytics);

            mockMvc.perform(get("/analytics"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getAnalytics_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/analytics"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "OPERATIONS_MANAGER")
        @DisplayName("returns 200 for operations manager")
        void getAnalytics_OperationsManager_Returns200() throws Exception {
            when(analyticsService.getAnalytics()).thenReturn(sampleAnalytics);

            mockMvc.perform(get("/analytics"))
                    .andExpect(status().isOk());
        }
    }
}
