package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.CreateBlogRequest;
import com.fyp.floodmonitoring.dto.request.UpdateBlogRequest;
import com.fyp.floodmonitoring.dto.response.BlogDto;
import com.fyp.floodmonitoring.entity.Blog;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.BlogRepository;
import com.fyp.floodmonitoring.repository.UserRepository;
import com.fyp.floodmonitoring.service.notifications.InAppProvider;
import com.fyp.floodmonitoring.service.notifications.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final InAppProvider inAppProvider;

    // ── Public read ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BlogDto> getFeaturedBlogs() {
        return blogRepository.findByIsFeaturedTrueOrderByCreatedAtDesc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<BlogDto> getAllBlogs(int page, int size, String category) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 50));
        String normalized = category != null ? category.strip() : null;
        Page<Blog> blogs = (normalized != null && !normalized.isBlank())
                ? blogRepository.findByCategoryNormalized(normalized, pageable)
                : blogRepository.findAllByOrderByCreatedAtDesc(pageable);
        return blogs.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<String> getDistinctBlogCategories() {
        return blogRepository.findDistinctCategoriesTrimmed();
    }

    @Transactional(readOnly = true)
    public BlogDto getBlogById(UUID id) {
        return blogRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> AppException.notFound("Blog not found"));
    }

    // ── Admin CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public BlogDto createBlog(CreateBlogRequest req) {
        Blog blog = Blog.builder()
                .title(req.title().strip())
                .body(req.body().strip())
                .imageKey(req.imageKey() != null ? req.imageKey() : "blog-1")
                .imageUrl(req.imageUrl())
                .category(req.category() != null ? req.category() : "General")
                .isFeatured(req.isFeatured() != null ? req.isFeatured() : false)
                .build();
        Blog saved = blogRepository.save(blog);

        // Fan out an in-app notification to every regular customer so the
        // community is informed when a new blog post lands. Best-effort —
        // failures don't block the publish itself.
        try {
            String snippet = saved.getBody() == null ? "" : saved.getBody().replaceAll("\\s+", " ").trim();
            if (snippet.length() > 140) snippet = snippet.substring(0, 139) + "…";
            String link = "/blogs/" + saved.getId();
            String title = "New blog post: " + (saved.getTitle().length() > 80
                    ? saved.getTitle().substring(0, 79) + "…" : saved.getTitle());
            for (User u : userRepository.findAll()) {
                if (u == null || !"customer".equalsIgnoreCase(u.getRole())) continue;
                inAppProvider.deliver(u.getId(), new NotificationPayload(
                        "blog.new",
                        "info",
                        title,
                        snippet,
                        null,
                        link));
            }
        } catch (Exception e) {
            log.warn("[Blog] Failed to dispatch new-blog notifications: {}", e.getMessage());
        }

        return toDto(saved);
    }

    @Transactional
    public BlogDto updateBlog(UUID id, UpdateBlogRequest req) {
        Blog blog = blogRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Blog not found"));

        if (req.title() != null && !req.title().isBlank()) blog.setTitle(req.title().strip());
        if (req.body()  != null && !req.body().isBlank())  blog.setBody(req.body().strip());
        if (req.imageKey()  != null) blog.setImageKey(req.imageKey());
        if (req.imageUrl()  != null) blog.setImageUrl(req.imageUrl().isBlank() ? null : req.imageUrl().strip());
        if (req.category()  != null) blog.setCategory(req.category());
        if (req.isFeatured()!= null) blog.setIsFeatured(req.isFeatured());

        return toDto(blogRepository.save(blog));
    }

    @Transactional
    public void deleteBlog(UUID id) {
        if (!blogRepository.existsById(id)) throw AppException.notFound("Blog not found");
        blogRepository.deleteById(id);
    }

    @Transactional
    public BlogDto toggleFeatured(UUID id) {
        Blog blog = blogRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Blog not found"));
        blog.setIsFeatured(blog.getIsFeatured() == null || !blog.getIsFeatured());
        return toDto(blogRepository.save(blog));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BlogDto toDto(Blog b) {
        return new BlogDto(
                b.getId().toString(),
                b.getImageKey(),
                b.getImageUrl(),
                b.getCategory() != null ? b.getCategory() : "General",
                b.getTitle(),
                b.getBody(),
                Boolean.TRUE.equals(b.getIsFeatured()),
                b.getCreatedAt() != null ? b.getCreatedAt().toString() : null,
                b.getUpdatedAt() != null ? b.getUpdatedAt().toString() : null
        );
    }
}
