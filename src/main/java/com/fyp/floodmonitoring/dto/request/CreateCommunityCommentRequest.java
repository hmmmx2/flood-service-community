package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A comment body is capped at 1500 characters. Long enough for a
 * detailed reply, short enough that "wwwwww..." spam can't blow up
 * the comment thread. Parent id is a UUID string; bounded to a
 * sane length so a forged parentId cannot smuggle a payload through
 * validation.
 */
public record CreateCommunityCommentRequest(
        @NotBlank @Size(min = 1, max = 1500, message = "Comment must be 1500 characters or fewer") String content,
        /** Reply parent comment id; omit or null for top-level. */
        @Size(max = 64) String parentId
) {}
