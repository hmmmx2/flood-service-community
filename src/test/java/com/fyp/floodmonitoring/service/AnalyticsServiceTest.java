package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.AnalyticsDataDto;
import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.repository.EventRepository;
import com.fyp.floodmonitoring.repository.NodeRepository;
import com.fyp.floodmonitoring.util.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AnalyticsService}.
 *
 * <p>All repository queries are mocked. Tests verify that aggregations, chart
 * data arrays, and stat labels are composed correctly from mock data.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService Tests")
class AnalyticsServiceTest {

    @Mock private NodeRepository  nodeRepository;
    @Mock private EventRepository eventRepository;

    @InjectMocks private AnalyticsService analyticsService;

    private Node normalNode;
    private Node warningNode;

    @BeforeEach
    void setUp() {
        normalNode  = TestDataBuilder.buildNode();
        warningNode = TestDataBuilder.buildWarningNode();
    }

    /** Stubs all repository calls with sensible defaults for a "happy path" test. */
    private void stubAllRepositories(long activeSensors, long alerts24h, long dataPoints24h) {
        when(nodeRepository.countByIsDeadFalse()).thenReturn(activeSensors);
        when(eventRepository.countAlerts24h()).thenReturn(alerts24h);
        when(eventRepository.countDataPoints24h()).thenReturn(dataPoints24h);
        when(eventRepository.countEventsByDayLast7Days()).thenReturn(Collections.emptyList());
        when(eventRepository.countEventsByMonthLast5Months()).thenReturn(Collections.emptyList());
        when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc()).thenReturn(Collections.emptyList());
        when(eventRepository.countAlertsByState()).thenReturn(Collections.emptyList());
        when(eventRepository.findTop10RecentEvents()).thenReturn(Collections.emptyList());
    }

    // ── getAnalytics() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAnalytics()")
    class GetAnalytics {

        @Test
        @DisplayName("returns non-null AnalyticsDataDto with all required sections")
        void getAnalytics_HappyPath_ReturnsCompleteDto() {
            stubAllRepositories(42, 3, 1200);

            AnalyticsDataDto result = analyticsService.getAnalytics();

            assertThat(result).isNotNull();
            assertThat(result.stats()).isNotNull();
            assertThat(result.chartData()).isNotNull();
            assertThat(result.yearlyChartData()).isNotNull();
            assertThat(result.waterLevelByNode()).isNotNull();
            assertThat(result.floodByState()).isNotNull();
            assertThat(result.recentEvents()).isNotNull();
        }

        @Test
        @DisplayName("stats array contains exactly 3 entries (sensors, alerts, data points)")
        void getAnalytics_Stats_HasThreeEntries() {
            stubAllRepositories(42, 3, 1200);

            AnalyticsDataDto result = analyticsService.getAnalytics();

            assertThat(result.stats()).hasSize(3);
        }

        @Test
        @DisplayName("first stat reflects active sensor count from repository")
        void getAnalytics_ActiveSensors_CorrectCount() {
            stubAllRepositories(42, 0, 0);

            AnalyticsDataDto result = analyticsService.getAnalytics();

            AnalyticsDataDto.StatDto activeStat = result.stats().get(0);
            assertThat(activeStat.label()).isEqualTo("Active Sensors");
            assertThat(activeStat.value()).isEqualTo("42");
        }

        @Test
        @DisplayName("second stat reflects 24h alert count from repository")
        void getAnalytics_AlertCount_CorrectCount() {
            stubAllRepositories(0, 7, 0);

            AnalyticsDataDto result = analyticsService.getAnalytics();

            AnalyticsDataDto.StatDto alertStat = result.stats().get(1);
            assertThat(alertStat.label()).isEqualTo("Alerts (24h)");
            assertThat(alertStat.value()).isEqualTo("7");
        }

        @Test
        @DisplayName("chartData array has exactly 7 entries (last 7 days)")
        void getAnalytics_ChartData_HasSevenEntries() {
            stubAllRepositories(0, 0, 0);

            AnalyticsDataDto result = analyticsService.getAnalytics();

            assertThat(result.chartData()).hasSize(7);
        }

        @Test
        @DisplayName("yearlyChartData array has exactly 5 entries (last 5 months)")
        void getAnalytics_YearlyChartData_HasFiveEntries() {
            stubAllRepositories(0, 0, 0);

            AnalyticsDataDto result = analyticsService.getAnalytics();

            assertThat(result.yearlyChartData()).hasSize(5);
        }

        @Test
        @DisplayName("waterLevelByNode is empty when no active nodes")
        void getAnalytics_NoNodes_WaterLevelEmpty() {
            stubAllRepositories(0, 0, 0);

            AnalyticsDataDto result = analyticsService.getAnalytics();

            assertThat(result.waterLevelByNode()).isEmpty();
        }

        @Test
        @DisplayName("waterLevelByNode excludes dead nodes")
        void getAnalytics_DeadNode_ExcludedFromWaterLevel() {
            stubAllRepositories(1, 0, 0);
            Node dead = TestDataBuilder.buildDeadNode();
            when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc())
                    .thenReturn(List.of(dead));
            when(eventRepository.countEventsByDayLast7Days()).thenReturn(Collections.emptyList());
            when(eventRepository.countEventsByMonthLast5Months()).thenReturn(Collections.emptyList());
            when(eventRepository.countAlertsByState()).thenReturn(Collections.emptyList());
            when(eventRepository.findTop10RecentEvents()).thenReturn(Collections.emptyList());

            AnalyticsDataDto result = analyticsService.getAnalytics();

            assertThat(result.waterLevelByNode()).isEmpty();
        }

        @Test
        @DisplayName("floodByState is populated from repository flood-by-state query")
        void getAnalytics_FloodByState_PopulatedFromRepo() {
            when(nodeRepository.countByIsDeadFalse()).thenReturn(5L);
            when(eventRepository.countAlerts24h()).thenReturn(0L);
            when(eventRepository.countDataPoints24h()).thenReturn(0L);
            when(eventRepository.countEventsByDayLast7Days()).thenReturn(Collections.emptyList());
            when(eventRepository.countEventsByMonthLast5Months()).thenReturn(Collections.emptyList());
            when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc()).thenReturn(Collections.emptyList());
            java.util.List<Object[]> stateData = new java.util.ArrayList<>();
            stateData.add(new Object[]{"Sarawak", 5L});
            stateData.add(new Object[]{"Sabah", 2L});
            when(eventRepository.countAlertsByState()).thenReturn(stateData);
            when(eventRepository.findTop10RecentEvents()).thenReturn(Collections.emptyList());

            AnalyticsDataDto result = analyticsService.getAnalytics();

            assertThat(result.floodByState()).hasSize(2);
            assertThat(result.floodByState().get(0).state()).isEqualTo("Sarawak");
            assertThat(result.floodByState().get(0).total()).isEqualTo(5);
        }

        @Test
        @DisplayName("all chart data defaults to zero when no event data is available")
        void getAnalytics_NoEventData_AllChartDataZero() {
            stubAllRepositories(0, 0, 0);

            AnalyticsDataDto result = analyticsService.getAnalytics();

            assertThat(result.chartData()).containsOnly(0);
            assertThat(result.yearlyChartData()).containsOnly(0);
        }
    }
}
