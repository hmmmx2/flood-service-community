package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.*;
import com.fyp.floodmonitoring.dto.response.LoginResponseDto;
import com.fyp.floodmonitoring.dto.response.RegisterPendingDto;
import com.fyp.floodmonitoring.security.ratelimit.RateLimit;
import com.fyp.floodmonitoring.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Public authentication endpoints (no JWT required).
 *
 * <pre>
 * POST /auth/register               (returns RegisterPendingDto — code emailed)
 * POST /auth/verify-email           (consumes the code, returns a session)
 * POST /auth/resend-verification
 * POST /auth/login
 * POST /auth/refresh
 * POST /auth/forgot-password
 * POST /auth/verify-reset-code
 * POST /auth/reset-password
 * POST /auth/change-password  (authenticated — changes password using current password)
 * </pre>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Rate limit rationale (per QA Sprint-1 P0-5):
    //
    //   /auth/register             — anti-spam-signup. 5/IP/hour, 30/day.
    //   /auth/verify-email         — single-shot, no per-email rate; uses
    //                                attempt counter on the code itself.
    //   /auth/resend-verification  — prevent email-bombing real users.
    //   /auth/login                — anti-brute-force. 10 attempts per IP
    //                                per hour. Per-email check needs to
    //                                read the request body in the inter-
    //                                ceptor (left as a follow-up); for now,
    //                                an attacker on a single IP can't
    //                                cycle >10 attempts/hour against any
    //                                account, which kills credential
    //                                stuffing at scale.
    //   /auth/refresh              — burst-friendly (legit clients refresh
    //                                ~once per 15 min). 30/min handles
    //                                token-rotation churn on busy sessions.
    //   /auth/forgot-password      — anti-email-bomb. 5/IP/hour, 20/day.
    //   /auth/verify-reset-code    — anti-code-bruteforce. 10/IP/hour.
    //   /auth/reset-password       — anti-code-replay. 5/IP/hour.
    //   /auth/change-password      — authenticated; relies on per-user
    //                                keying. 10/hour.
    //
    // All limits are per-bucket; the interceptor reads the @RateLimit
    // annotation, increments a counter in Redis keyed by user-id (or
    // IP when unauthenticated), and returns HTTP 429 with a Retry-After
    // header when any window is exceeded. Fail-open on Redis outage so
    // the limiter never causes an availability incident.

    @PostMapping("/register")
    @RateLimit(key = "auth.register", perMinute = 3, perHour = 5, perDay = 30)
    public ResponseEntity<RegisterPendingDto> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.register(req));
    }

    @PostMapping("/verify-email")
    @RateLimit(key = "auth.verifyEmail", perMinute = 10, perHour = 30)
    public ResponseEntity<LoginResponseDto> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        return ResponseEntity.ok(authService.verifyEmail(req));
    }

    @PostMapping("/resend-verification")
    @RateLimit(key = "auth.resendVerification", perMinute = 1, perHour = 5)
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest req) {

        authService.resendVerification(req);
        return ResponseEntity.ok(Map.of(
                "message", "If an account exists for this email, a fresh verification code has been sent."));
    }

    @PostMapping("/login")
    @RateLimit(key = "auth.login", perMinute = 5, perHour = 10)
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    @RateLimit(key = "auth.refresh", perMinute = 30, perHour = 200)
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REFRESH_TOKEN", "message", "Refresh token is missing or invalid"));
        }
        String accessToken = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(Map.of("accessToken", accessToken, "refreshToken", refreshToken));
    }

    @PostMapping("/forgot-password")
    @RateLimit(key = "auth.forgotPassword", perMinute = 1, perHour = 5, perDay = 20)
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {

        String code = authService.forgotPassword(req);

        if (code != null) {
            // Development mode — return code in response so the mobile app can auto-fill it
            return ResponseEntity.ok(Map.of(
                    "message", "Reset code generated (dev mode)",
                    "code", code));
        }
        return ResponseEntity.ok(Map.of("message", "If an account exists, a reset code has been sent"));
    }

    @PostMapping("/verify-reset-code")
    @RateLimit(key = "auth.verifyResetCode", perMinute = 5, perHour = 10)
    public ResponseEntity<Map<String, String>> verifyResetCode(
            @Valid @RequestBody VerifyResetCodeRequest req) {

        authService.verifyResetCode(req);
        return ResponseEntity.ok(Map.of("message", "Code verified successfully"));
    }

    @PostMapping("/reset-password")
    @RateLimit(key = "auth.resetPassword", perMinute = 3, perHour = 5)
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {

        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    /**
     * Authenticated endpoint — changes the current user's password.
     * Requires the caller to provide their existing password for verification.
     * Unlike /reset-password, no email reset code is needed.
     */
    @PostMapping("/change-password")
    @RateLimit(key = "auth.changePassword", perMinute = 3, perHour = 10)
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChangePasswordRequest req) {

        UUID userId = UUID.fromString(principal.getUsername());
        authService.changePassword(userId, req);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
