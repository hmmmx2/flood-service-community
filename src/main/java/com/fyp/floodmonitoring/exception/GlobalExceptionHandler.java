package com.fyp.floodmonitoring.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized error handler — converts all exceptions into a consistent
 * JSON response body: {@code { "code": "...", "message": "..." }}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Structured API error response body. */
    public record ApiErrorResponse(String code, String message, Object details) {
        ApiErrorResponse(String code, String message) {
            this(code, message, null);
        }
    }

    // ── Domain error ──────────────────────────────────────────────────────────

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }

    // ── Bean Validation (@Valid) ───────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("VALIDATION_ERROR", "Request validation failed", fieldErrors));
    }

    // ── Spring Security ───────────────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse("FORBIDDEN", "Access denied"));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAll(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(new ApiErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
