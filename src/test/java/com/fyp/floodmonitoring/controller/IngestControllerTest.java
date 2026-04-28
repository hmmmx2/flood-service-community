package com.fyp.floodmonitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.request.IngestRequest;
import com.fyp.floodmonitoring.dto.response.IngestResponse;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.IngestService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = IngestController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = "app.ingest.api-key=test-ingest-key")
@DisplayName("IngestController Tests")
class IngestControllerTest {

    /** Matches the test property override above. */
    private static final String VALID_API_KEY = "test-ingest-key";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private IngestService ingestService;

    @Nested
    @DisplayName("POST /ingest")
    class Ingest {

        @Test
        @DisplayName("returns 202 Accepted on valid ingest payload with no alert")
        void ingest_ValidPayload_NoAlert_Returns202() throws Exception {
            IngestResponse response = new IngestResponse(true, "102782478", false);
            when(ingestService.ingest(any(IngestRequest.class))).thenReturn(response);

            IngestRequest req = new IngestRequest(
                "102782478", 1, Instant.now(), 1.0, 25.5, 80.0, 1.5533, 110.3592
            );

            mockMvc.perform(post("/ingest")
                    .header("X-API-Key", VALID_API_KEY)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.nodeId").value("102782478"))
                .andExpect(jsonPath("$.alertFired").value(false));
        }

        @Test
        @DisplayName("returns 202 with alertFired=true when level rises to critical")
        void ingest_CriticalLevel_AlertFired_Returns202() throws Exception {
            IngestResponse response = new IngestResponse(true, "102782478", true);
            when(ingestService.ingest(any(IngestRequest.class))).thenReturn(response);

            IngestRequest req = new IngestRequest(
                "102782478", 3, Instant.now(), 4.0, 26.0, 85.0, 1.5533, 110.3592
            );

            mockMvc.perform(post("/ingest")
                    .header("X-API-Key", VALID_API_KEY)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.alertFired").value(true));
        }

        @Test
        @DisplayName("returns 400 when nodeId is blank")
        void ingest_BlankNodeId_Returns400() throws Exception {
            IngestRequest req = new IngestRequest("", 1, null, null, null, null, null, null);

            mockMvc.perform(post("/ingest")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when level is null")
        void ingest_NullLevel_Returns400() throws Exception {
            IngestRequest req = new IngestRequest("102782478", null, null, null, null, null, null, null);

            mockMvc.perform(post("/ingest")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when level exceeds valid range (>3)")
        void ingest_LevelTooHigh_Returns400() throws Exception {
            IngestRequest req = new IngestRequest("102782478", 4, null, null, null, null, null, null);

            mockMvc.perform(post("/ingest")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when level is negative")
        void ingest_NegativeLevel_Returns400() throws Exception {
            IngestRequest req = new IngestRequest("102782478", -1, null, null, null, null, null, null);

            mockMvc.perform(post("/ingest")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("is publicly accessible without auth token (IoT devices)")
        void ingest_NoAuth_Returns202() throws Exception {
            IngestResponse response = new IngestResponse(true, "102782478", false);
            when(ingestService.ingest(any())).thenReturn(response);

            IngestRequest req = new IngestRequest("102782478", 0, null, null, null, null, null, null);

            // No JWT Authorization header — IoT devices authenticate via X-API-Key only
            mockMvc.perform(post("/ingest")
                    .header("X-API-Key", VALID_API_KEY)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("accepts payload with only required fields (optional fields null)")
        void ingest_MinimalPayload_Returns202() throws Exception {
            IngestResponse response = new IngestResponse(true, "NODE-123", false);
            when(ingestService.ingest(any())).thenReturn(response);

            // Only required fields: nodeId + level
            String minimalJson = """
                { "nodeId": "NODE-123", "level": 0 }
                """;

            mockMvc.perform(post("/ingest")
                    .header("X-API-Key", VALID_API_KEY)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(minimalJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.received").value(true));
        }
    }
}
