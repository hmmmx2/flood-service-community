package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.CreateBlogRequest;
import com.fyp.floodmonitoring.dto.request.UpdateBlogRequest;
import com.fyp.floodmonitoring.dto.response.BlogDto;
import com.fyp.floodmonitoring.entity.Blog;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.BlogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogRepository blogRepository;

    // ── Public read ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BlogDto> getFeaturedBlogs() {
        return blogRepository.findByIsFeaturedTrueOrderByCreatedAtDesc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<BlogDto> getAllBlogs(int page, int size, String category) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 50));
        Page<Blog> blogs = (category != null && !category.isBlank())
                ? blogRepository.findByCategoryOrderByCreatedAtDesc(category, pageable)
                : blogRepository.findAllByOrderByCreatedAtDesc(pageable);
        return blogs.map(this::toDto);
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
        return toDto(blogRepository.save(blog));
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
