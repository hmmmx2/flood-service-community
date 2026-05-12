package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCommunityCommentRequest(
        @NotBlank @Size(min = 1, max = 1500, message = "Comment must be 1500 characters or fewer") String content
) {}
