package com.fyp.floodmonitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.request.CreateBlogRequest;
import com.fyp.floodmonitoring.dto.response.BlogDto;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.BlogService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = BlogController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@Import(TestSecurityConfig.class)
@DisplayName("BlogController Tests")
class BlogControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private BlogService blogService;

    private BlogDto sampleBlog;

    @BeforeEach
    void setUp() {
        sampleBlog = new BlogDto(
            "blog-uuid-001", "flood-risk", null, "Education",
            "Understanding Flood Risk Levels",
            "Floods are categorised into four levels...",
            true, "2025-01-01T00:00:00Z", "2025-01-01T00:00:00Z"
        );
    }

    // ── GET /blogs/featured ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /blogs/featured")
    class GetFeaturedBlogs {

        @Test
        @DisplayName("returns 200 with featured blog list (public, no auth required)")
        void getFeatured_Returns200() throws Exception {
            when(blogService.getFeaturedBlogs()).thenReturn(List.of(sampleBlog));

            mockMvc.perform(get("/blogs/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("blog-uuid-001"))
                .andExpect(jsonPath("$[0].title").value("Understanding Flood Risk Levels"))
                .andExpect(jsonPath("$[0].isFeatured").value(true));
        }

        @Test
        @DisplayName("returns 200 with empty list when no featured blogs")
        void getFeatured_Empty_Returns200() throws Exception {
            when(blogService.getFeaturedBlogs()).thenReturn(List.of());

            mockMvc.perform(get("/blogs/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ── GET /blogs ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /blogs")
    class GetAllBlogs {

        @Test
        @DisplayName("returns 200 with paginated blog list")
        void getAllBlogs_Returns200Paginated() throws Exception {
            when(blogService.getAllBlogs(0, 20, null))
                .thenReturn(new PageImpl<>(List.of(sampleBlog), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/blogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Understanding Flood Risk Levels"));
        }

        @Test
        @DisplayName("supports pagination parameters")
        void getAllBlogs_WithPagination_Returns200() throws Exception {
            when(blogService.getAllBlogs(1, 5, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

            mockMvc.perform(get("/blogs?page=1&size=5"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("supports category filter")
        void getAllBlogs_WithCategory_Returns200() throws Exception {
            when(blogService.getAllBlogs(0, 20, "Education"))
                .thenReturn(new PageImpl<>(List.of(sampleBlog), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/blogs?category=Education"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("Education"));
        }
    }

    // ── GET /blogs/{id} ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /blogs/{id}")
    class GetBlogById {

        @Test
        @DisplayName("returns 200 with blog by ID")
        void getBlogById_ValidId_Returns200() throws Exception {
            UUID id = UUID.fromString("blog-uuid-001".hashCode() > 0
                ? "00000000-0000-0000-0000-000000000001"
                : "00000000-0000-0000-0000-000000000001");
            when(blogService.getBlogById(any())).thenReturn(sampleBlog);

            mockMvc.perform(get("/blogs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Understanding Flood Risk Levels"));
        }
    }

    // ── POST /blogs ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /blogs")
    class CreateBlog {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 201 with created blog for admin user")
        void createBlog_Admin_Returns201() throws Exception {
            when(blogService.createBlog(any())).thenReturn(sampleBlog);

            CreateBlogRequest req = new CreateBlogRequest(
                "New Blog Title", "Blog body content here...",
                "key-1", null, "Safety", true
            );

            mockMvc.perform(post("/blogs")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Understanding Flood Risk Levels"));
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("returns 403 for non-admin user")
        void createBlog_Customer_Returns403() throws Exception {
            CreateBlogRequest req = new CreateBlogRequest(
                "New Blog Title", "Blog body content here...",
                null, null, "Safety", false
            );

            mockMvc.perform(post("/blogs")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 400 when title is blank")
        void createBlog_BlankTitle_Returns400() throws Exception {
            CreateBlogRequest req = new CreateBlogRequest("", "Body content", null, null, null, null);

            mockMvc.perform(post("/blogs")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── DELETE /blogs/{id} ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /blogs/{id}")
    class DeleteBlog {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 204 on successful delete")
        void deleteBlog_Admin_Returns204() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(delete("/blogs/" + id).with(csrf()))
                .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("returns 403 for non-admin user")
        void deleteBlog_Customer_Returns403() throws Exception {
            mockMvc.perform(delete("/blogs/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
        }
    }
}
