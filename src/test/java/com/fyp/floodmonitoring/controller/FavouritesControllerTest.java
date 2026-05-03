package com.fyp.floodmonitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.request.AddFavouriteRequest;
import com.fyp.floodmonitoring.dto.response.FavouriteNodeDto;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.FavouritesService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = FavouritesController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@Import(TestSecurityConfig.class)
@DisplayName("FavouritesController Tests")
class FavouritesControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private FavouritesService favouritesService;

    private FavouriteNodeDto sampleFavourite;

    @BeforeEach
    void setUp() {
        sampleFavourite = new FavouriteNodeDto(
            "node-uuid-001", "102782478", "Node 102782478", "active", "1.2 km",
            List.of(110.3592, 1.5533), "Kuching", "Sungai Sarawak", "Sarawak",
            1, "2025-01-01T10:00:00Z", "2025-01-01T08:00:00Z"
        );
    }

    @Nested
    @DisplayName("GET /favourites")
    class GetFavourites {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("returns 200 with user's favourite nodes")
        void getFavourites_AuthenticatedUser_Returns200() throws Exception {
            when(favouritesService.getFavourites(any())).thenReturn(List.of(sampleFavourite));

            mockMvc.perform(get("/favourites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Node 102782478"))
                .andExpect(jsonPath("$[0].nodeId").value("102782478"))
                .andExpect(jsonPath("$[0].status").value("active"))
                .andExpect(jsonPath("$[0].favouritedAt").exists());
        }

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("returns 200 with empty list when no favourites")
        void getFavourites_NoFavourites_Returns200Empty() throws Exception {
            when(favouritesService.getFavourites(any())).thenReturn(List.of());

            mockMvc.perform(get("/favourites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getFavourites_NoAuth_Returns401() throws Exception {
            mockMvc.perform(get("/favourites"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /favourites")
    class AddFavourite {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("returns 200 with added favourite node")
        void addFavourite_ValidRequest_Returns200() throws Exception {
            when(favouritesService.addFavourite(any(), any())).thenReturn(sampleFavourite);

            UUID nodeId = UUID.randomUUID();
            AddFavouriteRequest req = new AddFavouriteRequest(nodeId.toString());

            mockMvc.perform(post("/favourites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Node 102782478"))
                .andExpect(jsonPath("$.favouritedAt").exists());
        }

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("returns 400 when nodeId is null")
        void addFavourite_NullNodeId_Returns400() throws Exception {
            mockMvc.perform(post("/favourites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nodeId\": null}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void addFavourite_NoAuth_Returns401() throws Exception {
            AddFavouriteRequest req = new AddFavouriteRequest(UUID.randomUUID().toString());

            mockMvc.perform(post("/favourites")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /favourites/{nodeId}")
    class RemoveFavourite {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("returns 204 on successful removal")
        void removeFavourite_ValidNodeId_Returns204() throws Exception {
            UUID nodeId = UUID.randomUUID();
            doNothing().when(favouritesService).removeFavourite(any(), eq(nodeId.toString()));

            mockMvc.perform(delete("/favourites/" + nodeId).with(csrf()))
                .andExpect(status().isNoContent());

            verify(favouritesService).removeFavourite(any(), eq(nodeId.toString()));
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void removeFavourite_NoAuth_Returns401() throws Exception {
            mockMvc.perform(delete("/favourites/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isUnauthorized());
        }
    }
}
