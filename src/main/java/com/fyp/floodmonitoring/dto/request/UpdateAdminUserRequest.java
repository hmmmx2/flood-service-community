package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateAdminUserRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 50)  String role,
        @Size(max = 20)  String status      // "active" | "inactive" — stored in a future column; ignored for now
) {}
