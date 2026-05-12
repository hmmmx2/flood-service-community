package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Title is capped at 120 to fit one tweet-ish line in a card and to
 * stop the "NoLetterLimitNoLetterLimit..." overflow flaw a friend
 * discovered. Body is capped at 4000 which is plenty for a flood
 * update without giving a single post a 20 KB blast radius.
 */
public record CreateCommunityPostRequest(
        @NotBlank @Size(max = 120, message = "Title must be 120 characters or fewer") String title,
        @NotBlank @Size(max = 4000, message = "Content must be 4000 characters or fewer") String content,
        // imageUrl accepts either a public URL or a base64 data URL from
        // the in-app uploader; the 200_000 ceiling fits a resized 1280x720
        // JPEG with headroom.
        @Size(max = 200_000) String imageUrl,
        @Size(max = 100) String groupSlug
) {}
