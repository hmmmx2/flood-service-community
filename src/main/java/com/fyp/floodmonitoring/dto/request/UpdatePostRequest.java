package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Partial-update request for a community post. Mirrors the caps on
 * {@link CreateCommunityPostRequest} so edits cannot bypass the post
 * limits enforced at create time.
 */
public record UpdatePostRequest(
        @Size(max = 120, message = "Title must be 120 characters or fewer") String title,
        @Size(max = 4000, message = "Content must be 4000 characters or fewer") String content,
        @Size(max = 200_000) String imageUrl,
        boolean removeImage
) {}
