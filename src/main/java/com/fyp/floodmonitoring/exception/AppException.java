package com.fyp.floodmonitoring.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Domain exception that maps directly to an HTTP error response.
 *
 * <p>Throw this anywhere in the service layer; the {@link GlobalExceptionHandler}
 * will convert it to a structured JSON error body:
 * <pre>{@code { "code": "NOT_FOUND", "message": "User not found" }}</pre></p>
 */
@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AppException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    // ── Convenience factory methods ───────────────────────────────────────────

    public static AppException badRequest(String code, String message) {
        return new AppException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static AppException unauthorized(String message) {
        return new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static AppException forbidden(String message) {
        return new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
