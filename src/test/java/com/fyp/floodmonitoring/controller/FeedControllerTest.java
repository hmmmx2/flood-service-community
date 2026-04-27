package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.CursorPageDto;
import com.fyp.floodmonitoring.dto.response.FeedItemDto;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.FeedService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = FeedController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@Import(TestSecurityConfig.class)
@DisplayName("FeedController Tests")
class FeedControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private FeedService feedService;

    private FeedItemDto buildFeedItem(String id, String kind, String title) {
        return new FeedItemDto(id, kind, title, "Description text",
                Instant.now().toString(), "102782478", "warning", 2.5, null);
    }

    @Nested
    @DisplayName("GET /feed")
    class GetFeed {

        @Test
        @WithMockUser
        @DisplayName("returns 200 with paginated feed items, no cursor")
        void getFeed_NoCursor_Returns200() throws Exception {
            CursorPageDto<FeedItemDto> page = new CursorPageDto<>(
                List.of(
                    buildFeedItem(UUID.randomUUID().toString(), "ALERT", "Critical Flood at Node 102782478"),
                    buildFeedItem(UUID.randomUUID().toString(), "UPDATE", "Water Level Normal - Node 102782479")
                ),
                "next-cursor-uuid", true
            );
            when(feedService.getFeed(null)).thenReturn(page);

            mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor-uuid"))
                .andExpect(jsonPath("$.data[0].kind").value("ALERT"))
                .andExpect(jsonPath("$.data[1].kind").value("UPDATE"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with next page when cursor provided")
        void getFeed_WithCursor_Returns200() throws Exception {
            String cursor = UUID.randomUUID().toString();
            CursorPageDto<FeedItemDto> page = new CursorPageDto<>(
                List.of(buildFeedItem(UUID.randomUUID().toString(), "UPDATE", "Status Update")),
                null, false
            );
            when(feedService.getFeed(cursor)).thenReturn(page);

            mockMvc.perform(get("/feed?cursor=" + cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with empty content when no events")
        void getFeed_EmptyContent_Returns200() throws Exception {
            when(feedService.getFeed(null)).thenReturn(new CursorPageDto<>(List.of(), null, false));

            mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false));
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void getFeed_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/feed"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /feed/{id}")
    class GetFeedItem {

        @Test
        @WithMockUser
        @DisplayName("returns 200 with single feed item by ID")
        void getFeedItem_ValidId_Returns200() throws Exception {
            String id = UUID.randomUUID().toString();
            FeedItemDto item = buildFeedItem(id, "ALERT", "Critical Flood at Node 102782478");
            when(feedService.getFeedItem(id)).thenReturn(item);

            mockMvc.perform(get("/feed/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.kind").value("ALERT"))
                .andExpect(jsonPath("$.title").value("Critical Flood at Node 102782478"))
                .andExpect(jsonPath("$.sensorId").value("102782478"));
        }
    }
}
