package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.DashboardNodeRowDto;
import com.fyp.floodmonitoring.dto.response.DashboardTimeSeriesDto;
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
 * Unit tests for {@link DashboardService}.
 *
 * <p>Verifies node status mapping, status label assignment, and time-series
 * aggregation logic without requiring a real database.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService Tests")
class DashboardServiceTest {

    @Mock private NodeRepository  nodeRepository;
    @Mock private EventRepository eventRepository;

    @InjectMocks private DashboardService dashboardService;

    private Node normalNode;
    private Node warningNode;
    private Node criticalNode;
    private Node deadNode;

    @BeforeEach
    void setUp() {
        normalNode   = TestDataBuilder.buildNode();
        warningNode  = TestDataBuilder.buildWarningNode();
        criticalNode = TestDataBuilder.buildCriticalNode();
        deadNode     = TestDataBuilder.buildDeadNode();
    }

    // ── getDashboardNodes() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDashboardNodes()")
    class GetDashboardNodes {

        @Test
        @DisplayName("returns all nodes as row DTOs")
        void getDashboardNodes_ReturnsAllNodes() {
            when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc())
                    .thenReturn(List.of(criticalNode, warningNode, normalNode));

            List<DashboardNodeRowDto> result = dashboardService.getDashboardNodes();

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("maps level 3 node to 'Critical' status")
        void getDashboardNodes_Level3_MapsToCritical() {
            when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc())
                    .thenReturn(List.of(criticalNode));

            List<DashboardNodeRowDto> result = dashboardService.getDashboardNodes();

            assertThat(result.get(0).status()).isEqualTo("Critical");
        }

        @Test
        @DisplayName("maps level 2 node to 'Warning' status")
        void getDashboardNodes_Level2_MapsToWarning() {
            when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc())
                    .thenReturn(List.of(warningNode));

            List<DashboardNodeRowDto> result = dashboardService.getDashboardNodes();

            assertThat(result.get(0).status()).isEqualTo("Warning");
        }

        @Test
        @DisplayName("maps level 1 node to 'Normal' status")
        void getDashboardNodes_Level1_MapsToNormal() {
            when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc())
                    .thenReturn(List.of(normalNode));

            List<DashboardNodeRowDto> result = dashboardService.getDashboardNodes();

            assertThat(result.get(0).status()).isEqualTo("Normal");
        }

        @Test
        @DisplayName("maps dead node to 'Normal' status (no water risk)")
        void getDashboardNodes_DeadNode_MapsToNormal() {
            when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc())
                    .thenReturn(List.of(deadNode));

            List<DashboardNodeRowDto> result = dashboardService.getDashboardNodes();

            assertThat(result.get(0).status()).isEqualTo("Normal");
        }

        @Test
        @DisplayName("returns empty list when no nodes exist")
        void getDashboardNodes_NoNodes_ReturnsEmpty() {
            when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc())
                    .thenReturn(Collections.emptyList());

            List<DashboardNodeRowDto> result = dashboardService.getDashboardNodes();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("row DTO contains area, location, state fields")
        void getDashboardNodes_RowDtoHasExpectedFields() {
            when(nodeRepository.findAllByOrderByCurrentLevelDescNodeIdAsc())
                    .thenReturn(List.of(normalNode));

            List<DashboardNodeRowDto> result = dashboardService.getDashboardNodes();

            DashboardNodeRowDto row = result.get(0);
            assertThat(row.area()).isEqualTo("Kuching");
            assertThat(row.location()).isEqualTo("Sungai Sarawak");
            assertThat(row.state()).isEqualTo("Sarawak");
        }
    }

    // ── getTimeSeries() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTimeSeries()")
    class GetTimeSeries {

        @Test
        @DisplayName("returns 12 monthly data points")
        void getTimeSeries_Returns12MonthlyPoints() {
            when(eventRepository.countEventsByMonthLast12Months()).thenReturn(Collections.emptyList());
            when(eventRepository.countEventsByYearLast5Years()).thenReturn(Collections.emptyList());

            DashboardTimeSeriesDto result = dashboardService.getTimeSeries();

            assertThat(result.monthly()).hasSize(12);
        }

        @Test
        @DisplayName("returns 5 yearly data points")
        void getTimeSeries_Returns5YearlyPoints() {
            when(eventRepository.countEventsByMonthLast12Months()).thenReturn(Collections.emptyList());
            when(eventRepository.countEventsByYearLast5Years()).thenReturn(Collections.emptyList());

            DashboardTimeSeriesDto result = dashboardService.getTimeSeries();

            assertThat(result.yearly()).hasSize(5);
        }

        @Test
        @DisplayName("returns all zeros when no event data available")
        void getTimeSeries_NoData_ReturnsAllZeros() {
            when(eventRepository.countEventsByMonthLast12Months()).thenReturn(Collections.emptyList());
            when(eventRepository.countEventsByYearLast5Years()).thenReturn(Collections.emptyList());

            DashboardTimeSeriesDto result = dashboardService.getTimeSeries();

            assertThat(result.monthly()).containsOnly(0);
            assertThat(result.yearly()).containsOnly(0);
        }

        @Test
        @DisplayName("maps event counts from repository to correct monthly positions")
        void getTimeSeries_WithEventData_MapsCorrectly() {
            String currentMonth = java.time.YearMonth.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));

            java.util.List<Object[]> monthData = new java.util.ArrayList<>();
            monthData.add(new Object[]{currentMonth, 42L});
            when(eventRepository.countEventsByMonthLast12Months()).thenReturn(monthData);
            when(eventRepository.countEventsByYearLast5Years()).thenReturn(Collections.emptyList());

            DashboardTimeSeriesDto result = dashboardService.getTimeSeries();

            assertThat(result.monthly()).hasSize(12);
            assertThat(result.monthly().get(11)).isEqualTo(42);
        }

        @Test
        @DisplayName("maps yearly event counts correctly")
        void getTimeSeries_WithYearlyData_MapsCorrectly() {
            String currentYear = String.valueOf(java.time.Year.now().getValue());

            java.util.List<Object[]> yearData = new java.util.ArrayList<>();
            yearData.add(new Object[]{currentYear, 15L});
            when(eventRepository.countEventsByMonthLast12Months()).thenReturn(Collections.emptyList());
            when(eventRepository.countEventsByYearLast5Years()).thenReturn(yearData);

            DashboardTimeSeriesDto result = dashboardService.getTimeSeries();

            assertThat(result.yearly()).hasSize(5);
            assertThat(result.yearly().get(4)).isEqualTo(15);
        }
    }
}
