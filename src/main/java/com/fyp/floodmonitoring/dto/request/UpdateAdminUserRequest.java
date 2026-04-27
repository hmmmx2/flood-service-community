package com.fyp.floodmonitoring.dto.request;

public record UpdateAdminUserRequest(
        String firstName,
        String lastName,
        String role,
        String status      // "active" | "inactive" — stored in a future column; ignored for now
) {}
