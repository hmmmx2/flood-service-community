package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.CommunityGroupDto;
import com.fyp.floodmonitoring.dto.response.CommunityPostDto;
import com.fyp.floodmonitoring.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS_MANAGER','NGO_VOLUNTEER')")
public class AdminCommunityController {

    private final CommunityService communityService;

    @GetMapping("/posts")
    public ResponseEntity<Page<CommunityPostDto>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.max(1, Math.min(size, 50));
        return ResponseEntity.ok(
                communityService.listPosts(page, safeSize, "new", null, null, null));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        communityService.deletePost(id, null, true);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/groups")
    public ResponseEntity<List<CommunityGroupDto>> listGroups() {
        return ResponseEntity.ok(communityService.listGroups(null));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        communityService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}
