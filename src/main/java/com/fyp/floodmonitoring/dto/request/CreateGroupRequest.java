package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank @Size(max = 100)
        @Pattern(
            regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "Slug must start and end with a lowercase letter or digit, with single hyphens as separators (e.g. flood-alerts)"
        )
        String slug,
        @NotBlank @Size(max = 200) String name,
        String description,
        String iconColor
) {}
