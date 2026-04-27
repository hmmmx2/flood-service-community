package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.CreateBlogRequest;
import com.fyp.floodmonitoring.dto.request.UpdateBlogRequest;
import com.fyp.floodmonitoring.dto.response.BlogDto;
import com.fyp.floodmonitoring.service.BlogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Blog endpoints.
 *
 * Public: GET /blogs/featured, GET /blogs, GET /blogs/{id}
 * Admin:  POST /blogs, PUT /blogs/{id}, DELETE /blogs/{id}, PATCH /blogs/{id}/featured
 */
@RestController
@RequestMapping("/blogs")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;

    // ── Public ────────────────────────────────────────────────────────────────

    @GetMapping("/featured")
    public ResponseEntity<List<BlogDto>> getFeaturedBlogs() {
        return ResponseEntity.ok(blogService.getFeaturedBlogs());
    }

    @GetMapping
    public ResponseEntity<Page<BlogDto>> getAllBlogs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String category) {
        return ResponseEntity.ok(blogService.getAllBlogs(page, size, category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogDto> getBlog(@PathVariable UUID id) {
        return ResponseEntity.ok(blogService.getBlogById(id));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BlogDto> createBlog(@Valid @RequestBody CreateBlogRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(blogService.createBlog(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BlogDto> updateBlog(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateBlogRequest req) {
        return ResponseEntity.ok(blogService.updateBlog(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBlog(@PathVariable UUID id) {
        blogService.deleteBlog(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/featured")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BlogDto> toggleFeatured(@PathVariable UUID id) {
        return ResponseEntity.ok(blogService.toggleFeatured(id));
    }
}
