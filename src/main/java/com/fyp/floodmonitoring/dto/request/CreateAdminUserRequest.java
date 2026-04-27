package com.fyp.floodmonitoring.dto.request;

public record CreateAdminUserRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        String role        // "admin" | "customer"
) {}
