package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.CreateAdminUserRequest;
import com.fyp.floodmonitoring.dto.request.UpdateAdminUserRequest;
import com.fyp.floodmonitoring.dto.response.AdminUserDto;
import com.fyp.floodmonitoring.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only user management endpoints.
 * All routes require a valid JWT with role = 'ADMIN'.
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<List<AdminUserDto>> listUsers() {
        return ResponseEntity.ok(adminUserService.listAllUsers());
    }

    @PostMapping
    public ResponseEntity<AdminUserDto> createUser(@RequestBody CreateAdminUserRequest req) {
        return ResponseEntity.ok(adminUserService.createUser(req));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserDto> updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateAdminUserRequest req) {
        return ResponseEntity.ok(adminUserService.updateUser(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
