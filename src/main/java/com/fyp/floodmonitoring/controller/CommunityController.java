package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.CreateCommunityCommentRequest;
import com.fyp.floodmonitoring.dto.request.CreateCommunityPostRequest;
import com.fyp.floodmonitoring.dto.request.CreateGroupRequest;
import com.fyp.floodmonitoring.dto.request.UpdatePostRequest;
import com.fyp.floodmonitoring.dto.response.*;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    // ══ GROUP ENDPOINTS ═══════════════════════════════════════════════════════

    @GetMapping("/groups")
    public ResponseEntity<List<CommunityGroupDto>> listGroups(Authentication auth) {
        return ResponseEntity.ok(communityService.listGroups(resolveUserId(auth)));
    }

    @GetMapping("/groups/{slug}")
    public ResponseEntity<CommunityGroupDto> getGroup(@PathVariable String slug, Authentication auth) {
        return ResponseEntity.ok(communityService.getGroup(slug, resolveUserId(auth)));
    }

    /** Admin-only: create a group */
    @PostMapping("/groups")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CommunityGroupDto> createGroup(
            @Valid @RequestBody CreateGroupRequest req, Authentication auth) {
        return ResponseEntity.ok(communityService.createGroup(requireUserId(auth), req));
    }

    /** Admin-only: delete a group */
    @DeleteMapping("/groups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        communityService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    /** Users join/leave a group (toggle) */
    @PostMapping("/groups/{slug}/membership")
    public ResponseEntity<CommunityGroupDto> toggleMembership(
            @PathVariable String slug, Authentication auth) {
        return ResponseEntity.ok(communityService.toggleMembership(slug, requireUserId(auth)));
    }

    // ══ POST ENDPOINTS ════════════════════════════════════════════════════════

    @GetMapping("/posts")
    public ResponseEntity<Page<CommunityPostDto>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "new") String sort,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String search,
            Authentication auth) {
        // Whitelist sort values — reject anything other than "new" or "top"
        String safeSort = "top".equalsIgnoreCase(sort) ? "top" : "new";
        // Clamp page size to a safe range
        int safeSize = Math.max(1, Math.min(size, 100));
        return ResponseEntity.ok(
                communityService.listPosts(page, safeSize, safeSort, group, search, resolveUserId(auth)));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<CommunityPostDto> getPost(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(communityService.getPost(id, resolveUserId(auth)));
    }

    @PostMapping("/posts")
    public ResponseEntity<CommunityPostDto> createPost(
            @Valid @RequestBody CreateCommunityPostRequest req, Authentication auth) {
        return ResponseEntity.ok(communityService.createPost(requireUserId(auth), req));
    }

    @PatchMapping("/posts/{id}")
    public ResponseEntity<CommunityPostDto> updatePost(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePostRequest req,
            Authentication auth) {
        return ResponseEntity.ok(communityService.updatePost(id, requireUserId(auth), req));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id, Authentication auth) {
        communityService.deletePost(id, requireUserId(auth), hasRole(auth, "ROLE_ADMIN"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<LikeToggleDto> toggleLike(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(communityService.toggleLike(id, requireUserId(auth)));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<CommunityCommentDto> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCommunityCommentRequest req,
            Authentication auth) {
        return ResponseEntity.ok(communityService.addComment(id, requireUserId(auth), req));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID postId, @PathVariable UUID commentId, Authentication auth) {
        communityService.deleteComment(commentId, requireUserId(auth), hasRole(auth, "ROLE_ADMIN"));
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID resolveUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }

    private UUID requireUserId(Authentication auth) {
        UUID id = resolveUserId(auth);
        if (id == null) throw AppException.unauthorized("Authentication required");
        return id;
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }
}
