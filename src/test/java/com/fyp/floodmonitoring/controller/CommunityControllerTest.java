package com.fyp.floodmonitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.request.CreateCommunityCommentRequest;
import com.fyp.floodmonitoring.dto.request.CreateCommunityPostRequest;
import com.fyp.floodmonitoring.dto.request.CreateGroupRequest;
import com.fyp.floodmonitoring.dto.response.CommunityCommentDto;
import com.fyp.floodmonitoring.dto.response.CommunityGroupDto;
import com.fyp.floodmonitoring.dto.response.CommunityPostDto;
import com.fyp.floodmonitoring.dto.response.LikeToggleDto;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import com.fyp.floodmonitoring.service.CommunityService;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = CommunityController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@Import(TestSecurityConfig.class)
@DisplayName("CommunityController Tests")
class CommunityControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private CommunityService communityService;

    private CommunityGroupDto sampleGroup;
    private CommunityPostDto  samplePost;
    private CommunityCommentDto sampleComment;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        sampleGroup = new CommunityGroupDto(
            UUID.randomUUID().toString(),
            "flood-watch",
            "Flood Watch",
            "Community flood monitoring group",
            "F", "#1E88E5",
            42, 15, false, now
        );

        sampleComment = new CommunityCommentDto(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "Jane Smith", null,
            "Stay safe everyone!",
            now
        );

        samplePost = new CommunityPostDto(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "John Doe", null,
            null, null, null,
            "Flood Alert in Kuching",
            "Water levels rising near Satok area.",
            null,
            5, 2, false,
            now, now,
            List.of(sampleComment)
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GROUP ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /community/groups")
    class ListGroups {

        @Test
        @WithMockUser
        @DisplayName("returns 200 with list of groups for authenticated user")
        void listGroups_Authenticated_Returns200WithGroups() throws Exception {
            when(communityService.listGroups(any())).thenReturn(List.of(sampleGroup));

            mockMvc.perform(get("/community/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("flood-watch"))
                .andExpect(jsonPath("$[0].name").value("Flood Watch"))
                .andExpect(jsonPath("$[0].membersCount").value(42))
                .andExpect(jsonPath("$[0].postsCount").value(15));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 200 with empty list when no groups exist")
        void listGroups_NoGroups_ReturnsEmptyList() throws Exception {
            when(communityService.listGroups(any())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/community/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("returns 200 for unauthenticated request (public read endpoint)")
        void listGroups_NoAuth_Returns200() throws Exception {
            when(communityService.listGroups(isNull())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/community/groups"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /community/groups/{slug}")
    class GetGroup {

        @Test
        @WithMockUser
        @DisplayName("returns 200 with group details")
        void getGroup_ValidSlug_Returns200() throws Exception {
            when(communityService.getGroup(eq("flood-watch"), any())).thenReturn(sampleGroup);

            mockMvc.perform(get("/community/groups/flood-watch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("flood-watch"))
                .andExpect(jsonPath("$.name").value("Flood Watch"))
                .andExpect(jsonPath("$.iconLetter").value("F"));
        }
    }

    @Nested
    @DisplayName("POST /community/groups")
    class CreateGroup {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
        @DisplayName("admin can create a group — returns 200")
        void createGroup_Admin_Returns200() throws Exception {
            when(communityService.createGroup(any(), any())).thenReturn(sampleGroup);

            CreateGroupRequest req = new CreateGroupRequest(
                "flood-watch", "Flood Watch", "Monitor floods", "#1E88E5"
            );

            mockMvc.perform(post("/community/groups")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("flood-watch"));
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("customer cannot create a group — returns 403")
        void createGroup_Customer_Returns403() throws Exception {
            CreateGroupRequest req = new CreateGroupRequest(
                "flood-watch", "Flood Watch", "Monitor floods", "#1E88E5"
            );

            mockMvc.perform(post("/community/groups")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
        @DisplayName("returns 400 when slug is blank")
        void createGroup_BlankSlug_Returns400() throws Exception {
            // Create invalid JSON body with blank slug
            String invalidBody = "{\"slug\":\"\",\"name\":\"Flood Watch\"}";

            mockMvc.perform(post("/community/groups")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidBody))
                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
        @DisplayName("returns 400 when slug contains uppercase letters")
        void createGroup_InvalidSlugPattern_Returns400() throws Exception {
            String invalidBody = "{\"slug\":\"Flood-Watch\",\"name\":\"Flood Watch\"}";

            mockMvc.perform(post("/community/groups")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidBody))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /community/groups/{id}")
    class DeleteGroup {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
        @DisplayName("admin can delete a group — returns 204")
        void deleteGroup_Admin_Returns204() throws Exception {
            UUID groupId = UUID.randomUUID();
            doNothing().when(communityService).deleteGroup(groupId);

            mockMvc.perform(delete("/community/groups/" + groupId).with(csrf()))
                .andExpect(status().isNoContent());

            verify(communityService).deleteGroup(groupId);
        }

        @Test
        @WithMockUser(roles = "CUSTOMER")
        @DisplayName("customer cannot delete a group — returns 403")
        void deleteGroup_Customer_Returns403() throws Exception {
            mockMvc.perform(delete("/community/groups/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /community/groups/{slug}/membership")
    class ToggleMembership {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("toggles membership and returns updated group")
        void toggleMembership_Authenticated_Returns200() throws Exception {
            CommunityGroupDto joined = new CommunityGroupDto(
                sampleGroup.id(), sampleGroup.slug(), sampleGroup.name(),
                sampleGroup.description(), sampleGroup.iconLetter(), sampleGroup.iconColor(),
                43, sampleGroup.postsCount(), true, sampleGroup.createdAt()
            );
            when(communityService.toggleMembership(eq("flood-watch"), any())).thenReturn(joined);

            mockMvc.perform(post("/community/groups/flood-watch/membership")
                    .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinedByMe").value(true))
                .andExpect(jsonPath("$.membersCount").value(43));
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void toggleMembership_NoAuth_Returns401() throws Exception {
            mockMvc.perform(post("/community/groups/flood-watch/membership").with(csrf()))
                .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /community/posts")
    class ListPosts {

        @Test
        @WithMockUser
        @DisplayName("returns 200 with paginated posts")
        void listPosts_Authenticated_Returns200() throws Exception {
            PageImpl<CommunityPostDto> page = new PageImpl<>(
                List.of(samplePost), PageRequest.of(0, 20), 1
            );
            when(communityService.listPosts(anyInt(), anyInt(), anyString(), any(), any(), any()))
                .thenReturn(page);

            mockMvc.perform(get("/community/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Flood Alert in Kuching"))
                .andExpect(jsonPath("$.content[0].authorName").value("John Doe"))
                .andExpect(jsonPath("$.content[0].likesCount").value(5))
                .andExpect(jsonPath("$.content[0].commentsCount").value(2))
                .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @WithMockUser
        @DisplayName("accepts pagination and sort parameters")
        void listPosts_WithParams_PassesParamsToService() throws Exception {
            PageImpl<CommunityPostDto> page = new PageImpl<>(Collections.emptyList());
            when(communityService.listPosts(eq(1), eq(10), eq("top"), any(), any(), any()))
                .thenReturn(page);

            mockMvc.perform(get("/community/posts")
                    .param("page", "1")
                    .param("size", "10")
                    .param("sort", "top"))
                .andExpect(status().isOk());

            verify(communityService).listPosts(eq(1), eq(10), eq("top"), isNull(), any(), any());
        }

        @Test
        @WithMockUser
        @DisplayName("filters posts by group slug")
        void listPosts_WithGroupFilter_PassesGroupToService() throws Exception {
            PageImpl<CommunityPostDto> page = new PageImpl<>(List.of(samplePost));
            when(communityService.listPosts(anyInt(), anyInt(), anyString(), eq("flood-watch"), any(), any()))
                .thenReturn(page);

            mockMvc.perform(get("/community/posts")
                    .param("group", "flood-watch"))
                .andExpect(status().isOk());

            verify(communityService).listPosts(anyInt(), anyInt(), anyString(), eq("flood-watch"), any(), any());
        }

        @Test
        @DisplayName("returns 200 for unauthenticated request (public read endpoint)")
        void listPosts_NoAuth_Returns200() throws Exception {
            PageImpl<CommunityPostDto> page = new PageImpl<>(Collections.emptyList());
            when(communityService.listPosts(anyInt(), anyInt(), anyString(), isNull(), isNull(), isNull()))
                .thenReturn(page);

            mockMvc.perform(get("/community/posts"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /community/posts/{id}")
    class GetPost {

        @Test
        @WithMockUser
        @DisplayName("returns 200 with post details including comments")
        void getPost_ValidId_Returns200WithComments() throws Exception {
            UUID postId = UUID.fromString(samplePost.id());
            when(communityService.getPost(eq(postId), any())).thenReturn(samplePost);

            mockMvc.perform(get("/community/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.title").value("Flood Alert in Kuching"))
                .andExpect(jsonPath("$.comments.length()").value(1))
                .andExpect(jsonPath("$.comments[0].content").value("Stay safe everyone!"));
        }
    }

    @Nested
    @DisplayName("POST /community/posts")
    class CreatePost {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("authenticated user can create a post — returns 200")
        void createPost_Authenticated_Returns200() throws Exception {
            when(communityService.createPost(any(), any())).thenReturn(samplePost);

            CreateCommunityPostRequest req = new CreateCommunityPostRequest(
                "Flood Alert in Kuching",
                "Water levels rising near Satok area.",
                null, null
            );

            mockMvc.perform(post("/community/posts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Flood Alert in Kuching"))
                .andExpect(jsonPath("$.content").value("Water levels rising near Satok area."));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when title is blank")
        void createPost_BlankTitle_Returns400() throws Exception {
            String invalidBody = "{\"title\":\"\",\"content\":\"Some content\"}";

            mockMvc.perform(post("/community/posts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidBody))
                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when content is blank")
        void createPost_BlankContent_Returns400() throws Exception {
            String invalidBody = "{\"title\":\"A Title\",\"content\":\"\"}";

            mockMvc.perform(post("/community/posts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidBody))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void createPost_NoAuth_Returns401() throws Exception {
            String body = "{\"title\":\"Test\",\"content\":\"Content\"}";
            mockMvc.perform(post("/community/posts")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /community/posts/{id}")
    class DeletePost {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("post author can delete their own post — returns 204")
        void deletePost_Author_Returns204() throws Exception {
            UUID postId = UUID.fromString(samplePost.id());
            doNothing().when(communityService).deletePost(eq(postId), any(), anyBoolean());

            mockMvc.perform(delete("/community/posts/" + postId).with(csrf()))
                .andExpect(status().isNoContent());

            verify(communityService).deletePost(eq(postId), any(), anyBoolean());
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void deletePost_NoAuth_Returns401() throws Exception {
            mockMvc.perform(delete("/community/posts/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /community/posts/{id}/like")
    class ToggleLike {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("returns 200 with updated like count on toggle")
        void toggleLike_Authenticated_Returns200() throws Exception {
            UUID postId = UUID.fromString(samplePost.id());
            when(communityService.toggleLike(eq(postId), any()))
                .thenReturn(new LikeToggleDto(true, 6));

            mockMvc.perform(post("/community/posts/" + postId + "/like").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likesCount").value(6));
        }

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("returns 200 with decremented like count on unlike")
        void toggleLike_Unlike_Returns200() throws Exception {
            UUID postId = UUID.fromString(samplePost.id());
            when(communityService.toggleLike(eq(postId), any()))
                .thenReturn(new LikeToggleDto(false, 4));

            mockMvc.perform(post("/community/posts/" + postId + "/like").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likesCount").value(4));
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void toggleLike_NoAuth_Returns401() throws Exception {
            mockMvc.perform(post("/community/posts/" + UUID.randomUUID() + "/like").with(csrf()))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /community/posts/{id}/comments")
    class AddComment {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("authenticated user can add a comment — returns 200")
        void addComment_Authenticated_Returns200() throws Exception {
            UUID postId = UUID.fromString(samplePost.id());
            when(communityService.addComment(eq(postId), any(), any())).thenReturn(sampleComment);

            CreateCommunityCommentRequest req = new CreateCommunityCommentRequest("Stay safe everyone!");

            mockMvc.perform(post("/community/posts/" + postId + "/comments")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Stay safe everyone!"))
                .andExpect(jsonPath("$.authorName").value("Jane Smith"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when comment content is blank")
        void addComment_BlankContent_Returns400() throws Exception {
            UUID postId = UUID.fromString(samplePost.id());
            String invalidBody = "{\"content\":\"\"}";

            mockMvc.perform(post("/community/posts/" + postId + "/comments")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidBody))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void addComment_NoAuth_Returns401() throws Exception {
            mockMvc.perform(post("/community/posts/" + UUID.randomUUID() + "/comments")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"A comment\"}"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /community/posts/{postId}/comments/{commentId}")
    class DeleteComment {

        @Test
        @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
        @DisplayName("comment author can delete their comment — returns 204")
        void deleteComment_Author_Returns204() throws Exception {
            UUID postId    = UUID.fromString(samplePost.id());
            UUID commentId = UUID.fromString(sampleComment.id());
            doNothing().when(communityService).deleteComment(eq(commentId), any(), anyBoolean());

            mockMvc.perform(delete("/community/posts/" + postId + "/comments/" + commentId)
                    .with(csrf()))
                .andExpect(status().isNoContent());

            verify(communityService).deleteComment(eq(commentId), any(), anyBoolean());
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void deleteComment_NoAuth_Returns401() throws Exception {
            mockMvc.perform(delete("/community/posts/" + UUID.randomUUID()
                    + "/comments/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isUnauthorized());
        }
    }
}
