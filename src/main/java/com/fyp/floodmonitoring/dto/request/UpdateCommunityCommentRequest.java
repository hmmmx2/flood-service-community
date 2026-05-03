package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommunityCommentRequest(@NotBlank String content) {}
