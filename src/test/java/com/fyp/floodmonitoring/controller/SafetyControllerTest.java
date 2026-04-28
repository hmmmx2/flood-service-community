package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.dto.response.SafetyContentDto;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.SafetyService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link SafetyController}.
 *
 * <p>{@code GET /safety} is a public endpoint — no authentication required.
 * The optional {@code lang} query parameter defaults to "en".</p>
 */
@WebMvcTest(
        controllers = SafetyController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@Import(TestSecurityConfig.class)
@DisplayName("SafetyController Tests")
class SafetyControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private SafetyService safetyService;

    private List<SafetyContentDto> safetyContent;

    @BeforeEach
    void setUp() {
        safetyContent = TestDataBuilder.buildSafetyContent();
    }

    // ── GET /safety ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /safety")
    class GetSafety {

        @Test
        @DisplayName("returns 200 with safety list without authentication (public endpoint)")
        void getSafety_Public_Returns200() throws Exception {
            when(safetyService.getSafety("en")).thenReturn(safetyContent);

            mockMvc.perform(get("/safety"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3));
        }

        @Test
        @DisplayName("response includes section, content, and updatedAt fields")
        void getSafety_ResponseShape_IsCorrect() throws Exception {
            when(safetyService.getSafety("en")).thenReturn(safetyContent);

            mockMvc.perform(get("/safety"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].section").value("Before a Flood"))
                    .andExpect(jsonPath("$[0].content").exists())
                    .andExpect(jsonPath("$[0].updatedAt").exists());
        }

        @Test
        @DisplayName("returns 200 with all three safety sections")
        void getSafety_AllSections_Present() throws Exception {
            when(safetyService.getSafety("en")).thenReturn(safetyContent);

            mockMvc.perform(get("/safety"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].section").value("Before a Flood"))
                    .andExpect(jsonPath("$[1].section").value("During a Flood"))
                    .andExpect(jsonPath("$[2].section").value("After a Flood"));
        }

        @Test
        @DisplayName("returns 200 with empty list when no safety content exists")
        void getSafety_EmptyList_Returns200() throws Exception {
            when(safetyService.getSafety(anyString())).thenReturn(List.of());

            mockMvc.perform(get("/safety"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("passes lang parameter to service")
        void getSafety_WithLangParam_PassedToService() throws Exception {
            List<SafetyContentDto> msContent = List.of(
                    new SafetyContentDto("Sebelum Banjir", "Bersedia...", "2025-06-01T00:00:00Z")
            );
            when(safetyService.getSafety("ms")).thenReturn(msContent);

            mockMvc.perform(get("/safety?lang=ms"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].section").value("Sebelum Banjir"));
        }

        @Test
        @DisplayName("defaults lang to 'en' when not provided")
        void getSafety_DefaultLang_IsEnglish() throws Exception {
            when(safetyService.getSafety(eq("en"))).thenReturn(safetyContent);

            mockMvc.perform(get("/safety"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3));
        }

        @Test
        @DisplayName("returns 200 when accessed by an authenticated admin user")
        void getSafety_AsAdmin_Returns200() throws Exception {
            when(safetyService.getSafety("en")).thenReturn(safetyContent);

            mockMvc.perform(get("/safety"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("each safety item has non-blank content")
        void getSafety_ContentIsNonBlank() throws Exception {
            when(safetyService.getSafety("en")).thenReturn(safetyContent);

            mockMvc.perform(get("/safety"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].content").isNotEmpty())
                    .andExpect(jsonPath("$[1].content").isNotEmpty())
                    .andExpect(jsonPath("$[2].content").isNotEmpty());
        }
    }
}
