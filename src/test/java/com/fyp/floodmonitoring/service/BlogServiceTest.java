package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.CreateBlogRequest;
import com.fyp.floodmonitoring.dto.request.UpdateBlogRequest;
import com.fyp.floodmonitoring.dto.response.BlogDto;
import com.fyp.floodmonitoring.entity.Blog;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.BlogRepository;
import com.fyp.floodmonitoring.util.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BlogService}.
 *
 * <p>All repository interactions are mocked via Mockito. No database or Spring
 * context is required. Each nested class covers one service method.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BlogService Tests")
class BlogServiceTest {

    @Mock private BlogRepository blogRepository;

    @InjectMocks private BlogService blogService;

    private Blog sampleBlog;
    private Blog featuredBlog;

    @BeforeEach
    void setUp() {
        sampleBlog   = TestDataBuilder.buildBlog();
        featuredBlog = TestDataBuilder.buildFeaturedBlog();
    }

    // ── getFeaturedBlogs() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFeaturedBlogs()")
    class GetFeaturedBlogs {

        @Test
        @DisplayName("returns only isFeatured=true entries")
        void getFeaturedBlogs_ReturnsOnlyFeatured() {
            when(blogRepository.findByIsFeaturedTrueOrderByCreatedAtDesc())
                    .thenReturn(List.of(featuredBlog));

            List<BlogDto> result = blogService.getFeaturedBlogs();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isFeatured()).isTrue();
            assertThat(result.get(0).title()).isEqualTo("Understanding Flood Levels");
        }

        @Test
        @DisplayName("returns empty list when no featured blogs exist")
        void getFeaturedBlogs_EmptyWhenNone() {
            when(blogRepository.findByIsFeaturedTrueOrderByCreatedAtDesc())
                    .thenReturn(List.of());

            List<BlogDto> result = blogService.getFeaturedBlogs();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns multiple featured blogs in order")
        void getFeaturedBlogs_MultipleBlogs_Ordered() {
            Blog second = TestDataBuilder.buildFeaturedBlog();
            second.setId(UUID.randomUUID());
            second.setTitle("Second Featured Blog");

            when(blogRepository.findByIsFeaturedTrueOrderByCreatedAtDesc())
                    .thenReturn(List.of(featuredBlog, second));

            List<BlogDto> result = blogService.getFeaturedBlogs();

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(BlogDto::isFeatured);
        }
    }

    // ── getAllBlogs() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllBlogs()")
    class GetAllBlogs {

        @Test
        @DisplayName("returns paginated result without category filter")
        void getAllBlogs_NoCategoryFilter_ReturnsPaged() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(blogRepository.findAllByOrderByCreatedAtDesc(any()))
                    .thenReturn(new PageImpl<>(List.of(sampleBlog), pageable, 1));

            var page = blogService.getAllBlogs(0, 20, null);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("delegates to category-filtered query when category is provided")
        void getAllBlogs_WithCategory_UsesFilteredQuery() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(blogRepository.findByCategoryOrderByCreatedAtDesc(eq("Safety Tips"), any()))
                    .thenReturn(new PageImpl<>(List.of(sampleBlog), pageable, 1));

            var page = blogService.getAllBlogs(0, 20, "Safety Tips");

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).category()).isEqualTo("Safety Tips");
            verify(blogRepository).findByCategoryOrderByCreatedAtDesc(eq("Safety Tips"), any());
            verify(blogRepository, never()).findAllByOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("caps page size at 50 regardless of requested size")
        void getAllBlogs_SizeCappedAt50() {
            when(blogRepository.findAllByOrderByCreatedAtDesc(argThat(p -> p.getPageSize() == 50)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

            blogService.getAllBlogs(0, 200, null);

            verify(blogRepository).findAllByOrderByCreatedAtDesc(argThat(p -> p.getPageSize() == 50));
        }
    }

    // ── getBlogById() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getBlogById()")
    class GetBlogById {

        @Test
        @DisplayName("returns BlogDto when blog exists")
        void getBlogById_Found_ReturnsBlogDto() {
            UUID id = sampleBlog.getId();
            when(blogRepository.findById(id)).thenReturn(Optional.of(sampleBlog));

            BlogDto result = blogService.getBlogById(id);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(id.toString());
            assertThat(result.title()).isEqualTo("Flood Safety Tips");
        }

        @Test
        @DisplayName("throws AppException NOT_FOUND when blog does not exist")
        void getBlogById_NotFound_ThrowsAppException() {
            UUID id = UUID.randomUUID();
            when(blogRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> blogService.getBlogById(id))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(appEx.getCode()).isEqualTo("NOT_FOUND");
                    });
        }
    }

    // ── createBlog() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createBlog()")
    class CreateBlog {

        @Test
        @DisplayName("persists blog and returns mapped DTO")
        void createBlog_Valid_PersistsAndReturnsDto() {
            CreateBlogRequest req = new CreateBlogRequest(
                    "Flood Safety Tips", "Stay safe...", "blog-1", null, "Safety Tips", false
            );
            when(blogRepository.save(any(Blog.class))).thenReturn(sampleBlog);

            BlogDto result = blogService.createBlog(req);

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("Flood Safety Tips");
            assertThat(result.category()).isEqualTo("Safety Tips");
            verify(blogRepository).save(any(Blog.class));
        }

        @Test
        @DisplayName("defaults category to 'General' when not provided")
        void createBlog_NullCategory_DefaultsToGeneral() {
            Blog defaultCategoryBlog = TestDataBuilder.buildBlog();
            defaultCategoryBlog.setCategory("General");

            CreateBlogRequest req = new CreateBlogRequest(
                    "Test Blog", "Body content here", null, null, null, null
            );
            when(blogRepository.save(any(Blog.class))).thenReturn(defaultCategoryBlog);

            BlogDto result = blogService.createBlog(req);

            assertThat(result.category()).isEqualTo("General");
        }

        @Test
        @DisplayName("defaults imageKey to 'blog-1' when not provided")
        void createBlog_NullImageKey_DefaultsToBlog1() {
            CreateBlogRequest req = new CreateBlogRequest(
                    "Test Blog", "Body content here", null, null, "Safety", false
            );
            when(blogRepository.save(any(Blog.class))).thenReturn(sampleBlog);

            blogService.createBlog(req);

            verify(blogRepository).save(argThat(b -> "blog-1".equals(b.getImageKey())));
        }

        @Test
        @DisplayName("strips leading and trailing whitespace from title and body")
        void createBlog_WhitespaceTitle_IsStripped() {
            CreateBlogRequest req = new CreateBlogRequest(
                    "  Flood Safety Tips  ", "  Body text  ", null, null, null, null
            );
            when(blogRepository.save(any(Blog.class))).thenReturn(sampleBlog);

            blogService.createBlog(req);

            verify(blogRepository).save(argThat(b ->
                    "Flood Safety Tips".equals(b.getTitle()) &&
                    "Body text".equals(b.getBody())
            ));
        }
    }

    // ── updateBlog() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateBlog()")
    class UpdateBlog {

        @Test
        @DisplayName("updates and persists changed fields, returns updated DTO")
        void updateBlog_ValidRequest_UpdatesFields() {
            UUID id = sampleBlog.getId();
            when(blogRepository.findById(id)).thenReturn(Optional.of(sampleBlog));
            when(blogRepository.save(any(Blog.class))).thenReturn(sampleBlog);

            UpdateBlogRequest req = new UpdateBlogRequest(
                    "Updated Title", null, null, null, "Updates", true
            );

            BlogDto result = blogService.updateBlog(id, req);

            assertThat(result).isNotNull();
            verify(blogRepository).save(argThat(b -> "Updated Title".equals(b.getTitle())));
        }

        @Test
        @DisplayName("throws AppException NOT_FOUND when blog does not exist")
        void updateBlog_NotFound_ThrowsAppException() {
            UUID id = UUID.randomUUID();
            when(blogRepository.findById(id)).thenReturn(Optional.empty());

            UpdateBlogRequest req = new UpdateBlogRequest("New Title", null, null, null, null, null);

            assertThatThrownBy(() -> blogService.updateBlog(id, req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ── deleteBlog() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteBlog()")
    class DeleteBlog {

        @Test
        @DisplayName("deletes blog by ID when it exists")
        void deleteBlog_Exists_DeletesSuccessfully() {
            UUID id = sampleBlog.getId();
            when(blogRepository.existsById(id)).thenReturn(true);
            doNothing().when(blogRepository).deleteById(id);

            blogService.deleteBlog(id);

            verify(blogRepository).deleteById(id);
        }

        @Test
        @DisplayName("throws AppException NOT_FOUND when blog does not exist")
        void deleteBlog_NotFound_ThrowsAppException() {
            UUID id = UUID.randomUUID();
            when(blogRepository.existsById(id)).thenReturn(false);

            assertThatThrownBy(() -> blogService.deleteBlog(id))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

            verify(blogRepository, never()).deleteById(any());
        }
    }

    // ── toggleFeatured() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleFeatured()")
    class ToggleFeatured {

        @Test
        @DisplayName("sets isFeatured to true when currently false")
        void toggleFeatured_WasFalse_SetsTrue() {
            UUID id = sampleBlog.getId();
            sampleBlog.setIsFeatured(false);
            when(blogRepository.findById(id)).thenReturn(Optional.of(sampleBlog));
            when(blogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BlogDto result = blogService.toggleFeatured(id);

            assertThat(result.isFeatured()).isTrue();
        }

        @Test
        @DisplayName("sets isFeatured to false when currently true")
        void toggleFeatured_WasTrue_SetsFalse() {
            UUID id = featuredBlog.getId();
            featuredBlog.setIsFeatured(true);
            when(blogRepository.findById(id)).thenReturn(Optional.of(featuredBlog));
            when(blogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BlogDto result = blogService.toggleFeatured(id);

            assertThat(result.isFeatured()).isFalse();
        }

        @Test
        @DisplayName("treats null isFeatured as false and sets to true")
        void toggleFeatured_WasNull_SetsTrue() {
            UUID id = sampleBlog.getId();
            sampleBlog.setIsFeatured(null);
            when(blogRepository.findById(id)).thenReturn(Optional.of(sampleBlog));
            when(blogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BlogDto result = blogService.toggleFeatured(id);

            assertThat(result.isFeatured()).isTrue();
        }

        @Test
        @DisplayName("throws AppException NOT_FOUND when blog does not exist")
        void toggleFeatured_NotFound_ThrowsAppException() {
            UUID id = UUID.randomUUID();
            when(blogRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> blogService.toggleFeatured(id))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}
