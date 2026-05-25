package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.*;
import com.fyp.floodmonitoring.dto.response.*;
import com.fyp.floodmonitoring.entity.*;
import com.fyp.floodmonitoring.enums.Role;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.*;
import com.fyp.floodmonitoring.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Handles all authentication flows:
 * register · login · token refresh · forgot password · verify code · reset password.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final List<String> DEFAULT_SETTING_KEYS =
            List.of("pushNotifications", "smsNotifications", "emailNotifications", "lowDataMode");

    private final UserRepository                  userRepository;
    private final RefreshTokenRepository          refreshTokenRepository;
    private final PasswordResetCodeRepository     resetCodeRepository;
    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final UserSettingRepository           settingRepository;
    private final JwtTokenProvider                tokenProvider;
    private final PasswordEncoder                 passwordEncoder;
    private final EmailService                    emailService;
    private final com.fyp.floodmonitoring.security.RevokedTokenStore revokedTokenStore;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.environment}")
    private String environment;

    // ── Register ──────────────────────────────────────────────────────────────

    /**
     * Provisions a new (unverified) account and sends a 6-digit code to
     * the user's email. The user must call {@link #verifyEmail} with that
     * code before they can log in. Idempotent on the email side: if the
     * row already exists but is still {@code emailVerified=false}, we
     * regenerate + resend the code rather than failing — this lets a
     * user retry after losing the original email.
     */
    @Transactional
    public RegisterPendingDto register(RegisterRequest req) {
        String email = req.email().toLowerCase().trim();

        var existing = userRepository.findByEmail(email);
        User user;
        if (existing.isPresent()) {
            User row = existing.get();
            if (Boolean.TRUE.equals(row.getEmailVerified())) {
                throw AppException.conflict("An account with this email already exists");
            }
            // Unverified row — refresh password / name and reuse it so the
            // user can keep trying without "email already exists" errors.
            row.setFirstName(req.firstName().trim());
            row.setLastName(req.lastName().trim());
            row.setPasswordHash(passwordEncoder.encode(req.password()));
            user = userRepository.save(row);
        } else {
            user = User.builder()
                    .firstName(req.firstName().trim())
                    .lastName(req.lastName().trim())
                    .email(email)
                    .passwordHash(passwordEncoder.encode(req.password()))
                    .role(Role.CUSTOMER.getPersistenceValue())
                    .emailVerified(false)
                    .build();
            user = userRepository.save(user);
            seedDefaultSettings(user.getId());
        }

        String code = issueEmailVerificationCode(user.getId());
        emailService.sendRegistrationCode(email, code);
        log.info("[Auth] Registration verification code dispatched for {} [env={}]", email, environment);

        // In dev mode (no Resend key), surface the code so the local UI
        // can auto-fill it for testing without checking a real inbox.
        String devCode = "development".equals(environment) ? code : null;
        return new RegisterPendingDto(
                email,
                "Enter the 6-digit code we just emailed to confirm your account.",
                devCode);
    }

    /**
     * Verifies the registration code and returns a real session. The
     * account is marked {@code email_verified=true} on success.
     */
    @Transactional
    public LoginResponseDto verifyEmail(VerifyEmailRequest req) {
        String email = req.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("No registration found for this email"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            // Already verified — let the user just log in normally.
            throw AppException.conflict("This account is already verified — please sign in.");
        }

        EmailVerificationCode record = emailVerificationCodeRepository
                .findLatestUnused(user.getId(), req.code())
                .orElseThrow(() -> AppException.badRequest("INVALID_VERIFICATION_CODE", "Invalid verification code"));

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw AppException.badRequest("VERIFICATION_CODE_EXPIRED", "The verification code has expired");
        }

        record.setUsed(true);
        emailVerificationCodeRepository.save(record);

        user.setEmailVerified(true);
        userRepository.updateLastLogin(user.getId(), Instant.now());
        userRepository.save(user);

        return buildSession(user);
    }

    /**
     * Re-sends the verification code if the user lost the email. No-op
     * (silently) if the email is already verified — we don't want to
     * leak whether the address has an account.
     */
    @Transactional
    public void resendVerification(ResendVerificationRequest req) {
        String email = req.email().toLowerCase().trim();
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return;
        User user = userOpt.get();
        if (Boolean.TRUE.equals(user.getEmailVerified())) return;

        String code = issueEmailVerificationCode(user.getId());
        emailService.sendRegistrationCode(email, code);
        log.info("[Auth] Re-sent registration code to {} [env={}]", email, environment);
    }

    /** Invalidate any pending codes for the user, mint a fresh one, and persist it. */
    private String issueEmailVerificationCode(UUID userId) {
        emailVerificationCodeRepository.invalidateAllForUser(userId);
        String code = String.format("%06d", SECURE_RANDOM.nextInt(900000) + 100000);
        emailVerificationCodeRepository.save(EmailVerificationCode.builder()
                .userId(userId)
                .code(code)
                .expiresAt(Instant.now().plusSeconds(600)) // 10 min
                .build());
        return code;
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponseDto login(LoginRequest req) {
        String email = req.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.unauthorized("Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw AppException.unauthorized("Invalid email or password");
        }

        // Block sign-in for accounts whose email hasn't been confirmed
        // yet. Frontend recognises the EMAIL_NOT_VERIFIED code and bumps
        // the user to /verify-email so they can finish the flow.
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw AppException.badRequest(
                "EMAIL_NOT_VERIFIED",
                "Please verify your email before signing in.");
        }

        userRepository.updateLastLogin(user.getId(), Instant.now());
        return buildSession(user);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Server-side revocation of an access token + its paired refresh
     * token. Called by {@code POST /auth/logout}.
     *
     * <p>Two server-state effects, both best-effort:
     * <ol>
     *   <li><b>Access token</b> — adds its {@code jti} claim to
     *       {@link RevokedTokenStore} with a TTL equal to the remaining
     *       lifetime of the token. {@link JwtAuthenticationFilter}
     *       then rejects any further requests bearing this token,
     *       closing the "stolen access token replays for 15 min" gap.</li>
     *   <li><b>Refresh token</b> — when provided, deletes the matching
     *       row from {@code refresh_tokens}. The next refresh attempt
     *       with this token returns 401, forcing a fresh login.</li>
     * </ol>
     *
     * <p>Both arguments are nullable — the endpoint accepts logout
     * with either token alone. If both are missing it's a no-op
     * (idempotent). Per-jti revocation is what closes the security
     * gap; the refresh-token cleanup is hygiene.</p>
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // 1. Access-token revocation. We don't need the token to be
        //    structurally valid here — the filter has already accepted
        //    it before routing to this endpoint, so jti + exp are
        //    trustworthy. (We still defensively check that the parse
        //    succeeds and the jti is present.)
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                String jti = tokenProvider.getJtiFromAccessToken(accessToken);
                long expEpoch = tokenProvider.getExpirySecondsFromAccessToken(accessToken);
                long nowEpoch = System.currentTimeMillis() / 1000L;
                long ttl = expEpoch - nowEpoch;
                if (jti != null && !jti.isBlank() && ttl > 0) {
                    revokedTokenStore.revoke(jti, ttl);
                    log.info("[AuthService.logout] revoked jti={} ttl={}s", jti, ttl);
                }
            } catch (Exception e) {
                // Don't fail the logout just because the access token
                // is malformed — the cookies are already gone client-
                // side; revocation is defence-in-depth.
                log.warn("[AuthService.logout] could not revoke access token: {}", e.getMessage());
            }
        }

        // 2. Refresh-token cleanup. Idempotent — delete-or-nothing.
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                refreshTokenRepository.deleteByToken(refreshToken);
            } catch (Exception e) {
                log.warn("[AuthService.logout] refresh token cleanup failed: {}", e.getMessage());
            }
        }
    }

    // ── Refresh token ─────────────────────────────────────────────────────────

    @Transactional
    public String refreshAccessToken(String refreshToken) {
        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            throw AppException.unauthorized("Refresh token is invalid or expired");
        }

        UUID userId = tokenProvider.getSubjectFromRefreshToken(refreshToken);

        refreshTokenRepository.findValidToken(refreshToken, userId, Instant.now())
                .orElseThrow(() -> AppException.unauthorized("Refresh token has been revoked"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.unauthorized("User not found"));

        return tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    @Transactional
    public String forgotPassword(ForgotPasswordRequest req) {
        String email = req.email().toLowerCase().trim();
        var userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            // Do not reveal whether the account exists
            return null;
        }

        User user = userOpt.get();

        // Privileged accounts cannot self-reset via the public email
        // flow — admin email compromise is the easiest path to an
        // account takeover, so admins must change their password
        // through the authenticated /auth/change-password endpoint.
        // Silent log + null return so the response shape doesn't
        // betray the role.
        String role = user.getRole() != null ? user.getRole().toLowerCase() : "";
        if ("admin".equals(role) || "operations_manager".equals(role)) {
            log.warn("[Auth] Forgot-password blocked for privileged role={} email={}", role, email);
            return null;
        }

        resetCodeRepository.invalidateAllForUser(user.getId());

        String code = String.format("%06d", SECURE_RANDOM.nextInt(900000) + 100000);
        PasswordResetCode resetCode = PasswordResetCode.builder()
                .userId(user.getId())
                .code(code)
                .expiresAt(Instant.now().plusSeconds(600)) // 10 minutes
                .build();
        resetCodeRepository.save(resetCode);

        // Send email asynchronously — does not block the HTTP response
        emailService.sendPasswordResetCode(email, code);
        log.info("[Auth] Password reset code dispatched for {} [env={}]", email, environment);

        // In development mode (no RESEND_API_KEY), the code is also returned in the
        // response so the frontend can auto-fill it for testing without a real inbox.
        // In production this always returns null — the user must check their email.
        return "development".equals(environment) ? code : null;
    }

    // ── Verify reset code ─────────────────────────────────────────────────────

    @Transactional
    public void verifyResetCode(VerifyResetCodeRequest req) {
        String email = req.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("No reset request found for this email"));

        PasswordResetCode record = resetCodeRepository
                .findLatestUnused(user.getId(), req.code())
                .orElseThrow(() -> AppException.badRequest("INVALID_RESET_CODE", "Invalid verification code"));

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw AppException.badRequest("RESET_CODE_EXPIRED", "The verification code has expired");
        }

        record.setVerified(true);
        resetCodeRepository.save(record);
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String email = req.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("Account not found"));

        // Mirror the forgotPassword guard at the redemption step.
        // Catches the edge case where a code was minted before the
        // user got promoted to admin / operations_manager.
        String role = user.getRole() != null ? user.getRole().toLowerCase() : "";
        if ("admin".equals(role) || "operations_manager".equals(role)) {
            log.warn("[Auth] Reset-password blocked for privileged role={} email={}", role, email);
            throw AppException.forbidden(
                "Privileged accounts must change their password while signed in.");
        }

        PasswordResetCode record = resetCodeRepository
                .findLatestVerifiedUnused(user.getId())
                .orElseThrow(() -> AppException.forbidden("Password reset verification is required"));

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw AppException.badRequest("RESET_CODE_EXPIRED", "The verification code has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        record.setUsed(true);
        resetCodeRepository.save(record);

        // Revoke all refresh tokens for security
        refreshTokenRepository.deleteAllByUserId(user.getId());
    }

    // ── Change password (authenticated) ──────────────────────────────────────

    /**
     * Changes the password for an already-authenticated user.
     * Requires the correct current password — no reset-code flow needed.
     *
     * @param userId  the authenticated user's UUID
     * @param req     contains currentPassword + newPassword
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw AppException.unauthorized("Current password is incorrect");
        }

        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw AppException.badRequest("SAME_PASSWORD", "New password must differ from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // Revoke all existing refresh tokens for security after password change
        refreshTokenRepository.deleteAllByUserId(userId);
        log.info("[Auth] Password changed for userId={}", userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private LoginResponseDto buildSession(User user) {
        String accessToken  = tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = tokenProvider.createRefreshToken(user.getId());

        RefreshToken rt = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiryMs))
                .build();
        refreshTokenRepository.save(rt);

        Instant accessExpiresAt = Instant.now().plusMillis(accessTokenExpiryMs);
        AuthSessionDto session = new AuthSessionDto(accessToken, refreshToken, accessExpiresAt.toString());
        String displayName = (user.getFirstName() + " " + user.getLastName()).trim();
        UserSummaryDto userDto = new UserSummaryDto(
                user.getId().toString(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                displayName,
                user.getAvatarUrl(),
                Role.fromString(user.getRole()).getDisplayLabel());

        return new LoginResponseDto(session, userDto);
    }

    private void seedDefaultSettings(UUID userId) {
        for (String key : DEFAULT_SETTING_KEYS) {
            settingRepository.upsertDefault(userId, key);
        }
    }
}
