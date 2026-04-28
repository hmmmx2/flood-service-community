package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.SensorNodeDto;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.SensorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = SensorController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@Import(TestSecurityConfig.class)
@DisplayName("SensorController Tests")
class SensorControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private SensorService sensorService;

    private SensorNodeDto buildNode(String nodeId, String status, Integer level) {
        return new SensorNodeDto(
            "uuid-" + nodeId, nodeId, "Node " + nodeId, status,
            "1.2 km", List.of(110.3592, 1.5533),
            "Kuching", "Sungai Sarawak", "Sarawak",
            level, false, "2025-01-01T10:00:00Z", "2024-01-01T00:00:00Z",
            1.5533, 110.3592
        );
    }

    @Test
    @DisplayName("GET /sensors returns 200 with list of all nodes")
    void getSensors_Returns200WithNodeList() throws Exception {
        List<SensorNodeDto> nodes = List.of(
            buildNode("102782478", "active", 1),
            buildNode("102782479", "warning", 2),
            buildNode("102782480", "inactive", 0)
        );
        when(sensorService.getAllSensors()).thenReturn(nodes);

        mockMvc.perform(get("/sensors"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].nodeId").value("102782478"))
            .andExpect(jsonPath("$[0].status").value("active"))
            .andExpect(jsonPath("$[0].currentLevel").value(1))
            .andExpect(jsonPath("$[1].status").value("warning"))
            .andExpect(jsonPath("$[2].isDead").value(false));
    }

    @Test
    @DisplayName("GET /sensors returns 200 with empty list when no nodes")
    void getSensors_EmptyList_Returns200() throws Exception {
        when(sensorService.getAllSensors()).thenReturn(List.of());

        mockMvc.perform(get("/sensors"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /sensors is accessible without authentication (public endpoint)")
    void getSensors_NoAuth_Returns200() throws Exception {
        when(sensorService.getAllSensors()).thenReturn(List.of(buildNode("102782478", "active", 1)));

        // No auth token provided - should still work (public endpoint)
        mockMvc.perform(get("/sensors"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /sensors returns correct field structure")
    void getSensors_ReturnsCorrectFields() throws Exception {
        when(sensorService.getAllSensors()).thenReturn(List.of(buildNode("102782478", "active", 1)));

        mockMvc.perform(get("/sensors"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].nodeId").exists())
            .andExpect(jsonPath("$[0].name").exists())
            .andExpect(jsonPath("$[0].status").exists())
            .andExpect(jsonPath("$[0].distance").exists())
            .andExpect(jsonPath("$[0].coordinate").isArray())
            .andExpect(jsonPath("$[0].area").exists())
            .andExpect(jsonPath("$[0].currentLevel").exists());
    }
}
