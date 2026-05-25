package com.fyp.floodmonitoring.util;

import com.fyp.floodmonitoring.exception.AppException;

import java.util.Base64;
import java.util.Set;

public final class ImageDataUrlValidator {
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private ImageDataUrlValidator() {}

    public static String cleanNullableImageUrl(String value, int maxDecodedBytes) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isBlank()) return null;
        if (!trimmed.startsWith("data:")) return trimmed;

        int comma = trimmed.indexOf(',');
        if (comma < 0) {
            throw AppException.badRequest("INVALID_IMAGE", "Image data URL is malformed.");
        }
        String meta = trimmed.substring(5, comma).toLowerCase();
        String payload = trimmed.substring(comma + 1);
        String mime = meta.split(";", 2)[0];

        if (!ALLOWED_MIME_TYPES.contains(mime) || !meta.contains(";base64")) {
            throw AppException.badRequest("INVALID_IMAGE", "Only base64 JPEG, PNG, WebP, or GIF images are allowed.");
        }
        if ("image/svg+xml".equals(mime) || meta.contains("svg")) {
            throw AppException.badRequest("INVALID_IMAGE", "SVG images are not allowed for user uploads.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            throw AppException.badRequest("INVALID_IMAGE", "Image data URL is not valid base64.");
        }
        if (decoded.length > maxDecodedBytes) {
            throw AppException.badRequest("IMAGE_TOO_LARGE", "Image upload exceeds the maximum allowed size.");
        }
        return trimmed;
    }
}
