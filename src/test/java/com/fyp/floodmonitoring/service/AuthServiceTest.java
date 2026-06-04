package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.ForgotPasswordRequest;
import com.fyp.floodmonitoring.dto.request.LoginRequest;
import com.fyp.floodmonitoring.dto.request.RegisterRequest;
import com.fyp.floodmonitoring.dto.request.ResendVerificationRequest;
import com.fyp.floodmonitoring.dto.request.ResetPasswordRequest;
import com.fyp.floodmonitoring.dto.request.VerifyEmailRequest;
import com.fyp.floodmonitoring.dto.request.VerifyResetCodeRequest;
import com.fyp.floodmonitoring.dto.response.LoginResponseDto;
import com.fyp.floodmonitoring.dto.response.RegisterPendingDto;
import com.fyp.floodmonitoring.entity.EmailVerificationCode;
import com.fyp.floodmonitoring.entity.PasswordResetCode;
import com.fyp.floodmonitoring.entity.RefreshToken;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.EmailVerificationCodeRepository;
import com.fyp.floodmonitoring.repository.PasswordResetCodeRepository;
import com.fyp.floodmonitoring.repository.RefreshTokenRepository;
import com.fyp.floodmonitoring.repository.UserRepository;
import com.fyp.floodmonitoring.repository.UserSettingRepository;
import com.fyp.floodmonitoring.security.JwtTokenProvider;
import com.fyp.floodmonitoring.security.RevokedTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>Covers the full email-verification registration flow (register → verify →
 * login), the password-reset flow (forgot → verify-reset-code → reset), and the
 * security gates that protect them. Reconciled in Sprint-4 QA: registration now
 * returns a {@link RegisterPendingDto} (no session) and login is gated behind
 * {@code emailVerified}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock private UserRepository                  userRepository;
    @Mock private RefreshTokenRepository          refreshTokenRepository;
    @Mock private PasswordResetCodeRepository     resetCodeRepository;
    @Mock private EmailVerificationCodeRepository emailVerificationCodeRepository;
    @Mock private UserSettingRepository           settingRepository;
    @Mock private JwtTokenProvider                tokenProvider;
    @Mock private PasswordEncoder                 passwordEncoder;
    @Mock private EmailService                    emailService;
    @Mock private RevokedTokenStore               revokedTokenStore;

    @InjectMocks private AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        mockUser.setFirstName("John");
        mockUser.setLastName("Doe");
        mockUser.setEmail("john@example.com");
        mockUser.setPasswordHash("$2a$12$hashed_password");
        mockUser.setRole("customer");
        mockUser.setEmailVerified(true); // verified by default; tests flip to false where needed
        mockUser.setCreatedAt(Instant.now());
        mockUser.setUpdatedAt(Instant.now());
    }

    private User unverifiedUser(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFirstName("Jane");
        u.setLastName("Roe");
        u.setEmail(email);
        u.setPasswordHash("$2a$12$old");
        u.setRole("customer");
        u.setEmailVerified(false);
        return u;
    }

    // ── Register ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("new user → returns pending-verification, seeds 4 defaults, emails a code")
        void register_NewUser_ReturnsPendingAndSeedsDefaults() {
            RegisterRequest req = new RegisterRequest("John", "Doe", "John@Example.com", "Password@123");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Password@123")).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenReturn(mockUser);

            RegisterPendingDto res = authService.register(req);

            assertThat(res).isNotNull();
            assertThat(res.email()).isEqualTo("john@example.com");
            assertThat(res.message()).isNotBlank();
            assertThat(res.devCode()).isNull(); // not development env → never leaked

            verify(userRepository).save(any(User.class));
            verify(settingRepository, times(4)).upsertDefault(eq(mockUser.getId()), anyString());
            verify(emailVerificationCodeRepository).save(any(EmailVerificationCode.class));
            verify(emailService).sendRegistrationCode(eq("john@example.com"), anyString());
        }

        @Test
        @DisplayName("duplicate VERIFIED email → 409 conflict, no save")
        void register_DuplicateVerifiedEmail_ThrowsConflict() {
            RegisterRequest req = new RegisterRequest("John", "Doe", "john@example.com", "Password@123");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser)); // verified

            assertThatThrownBy(() -> authService.register(req))
                .isInstanceOfSatisfying(AppException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

            verify(userRepository, never()).save(any());
            verify(emailService, never()).sendRegistrationCode(anyString(), anyString());
        }

        @Test
        @DisplayName("existing UNVERIFIED email → reuses the row, does NOT re-seed settings")
        void register_UnverifiedEmail_ReusesRowWithoutReseeding() {
            User existing = unverifiedUser("unverified@example.com");
            RegisterRequest req = new RegisterRequest("Jane", "Roe", "unverified@example.com", "NewPass@123");

            when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(existing));
            when(passwordEncoder.encode("NewPass@123")).thenReturn("$2a$12$new");
            when(userRepository.save(any(User.class))).thenReturn(existing);

            RegisterPendingDto res = authService.register(req);

            assertThat(res.email()).isEqualTo("unverified@example.com");
            verify(userRepository).save(existing);
            verify(settingRepository, never()).upsertDefault(any(), anyString()); // reuse path skips seeding
            verify(emailVerificationCodeRepository).save(any(EmailVerificationCode.class));
        }
    }

    // ── Verify email ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyEmail()")
    class VerifyEmail {

        @Test
        @DisplayName("valid code → marks verified and returns a session")
        void verifyEmail_ValidCode_ReturnsSession() {
            mockUser.setEmailVerified(false);
            VerifyEmailRequest req = new VerifyEmailRequest("john@example.com", "123456");

            EmailVerificationCode rec = EmailVerificationCode.builder()
                    .userId(mockUser.getId()).code("123456")
                    .expiresAt(Instant.now().plusSeconds(300)).build();

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(emailVerificationCodeRepository.findLatestUnused(mockUser.getId(), "123456"))
                    .thenReturn(Optional.of(rec));
            when(tokenProvider.createAccessToken(any(), anyString(), anyString())).thenReturn("access");
            when(tokenProvider.createRefreshToken(any())).thenReturn("refresh");

            LoginResponseDto res = authService.verifyEmail(req);

            assertThat(res.session().accessToken()).isEqualTo("access");
            assertThat(mockUser.getEmailVerified()).isTrue();
            assertThat(rec.getUsed()).isTrue();
            verify(emailVerificationCodeRepository).save(rec);
            verify(userRepository).updateLastLogin(eq(mockUser.getId()), any());
        }

        @Test
        @DisplayName("wrong code → 400 INVALID_VERIFICATION_CODE")
        void verifyEmail_WrongCode_ThrowsBadRequest() {
            mockUser.setEmailVerified(false);
            VerifyEmailRequest req = new VerifyEmailRequest("john@example.com", "000000");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(emailVerificationCodeRepository.findLatestUnused(mockUser.getId(), "000000"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyEmail(req))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("INVALID_VERIFICATION_CODE");
                });
        }

        @Test
        @DisplayName("expired code → 400 VERIFICATION_CODE_EXPIRED")
        void verifyEmail_ExpiredCode_ThrowsExpired() {
            mockUser.setEmailVerified(false);
            VerifyEmailRequest req = new VerifyEmailRequest("john@example.com", "123456");

            EmailVerificationCode expired = EmailVerificationCode.builder()
                    .userId(mockUser.getId()).code("123456")
                    .expiresAt(Instant.now().minusSeconds(1)).build();

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(emailVerificationCodeRepository.findLatestUnused(mockUser.getId(), "123456"))
                    .thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.verifyEmail(req))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("VERIFICATION_CODE_EXPIRED");
                });
        }

        @Test
        @DisplayName("already-verified account → 409 conflict")
        void verifyEmail_AlreadyVerified_ThrowsConflict() {
            VerifyEmailRequest req = new VerifyEmailRequest("john@example.com", "123456");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser)); // verified

            assertThatThrownBy(() -> authService.verifyEmail(req))
                .isInstanceOfSatisfying(AppException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
    }

    // ── Resend verification ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("resendVerification()")
    class ResendVerification {

        @Test
        @DisplayName("unknown email → silent no-op (no enumeration)")
        void resend_UnknownEmail_NoOp() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            authService.resendVerification(new ResendVerificationRequest("ghost@example.com"));

            verify(emailVerificationCodeRepository, never()).save(any());
            verify(emailService, never()).sendRegistrationCode(anyString(), anyString());
        }

        @Test
        @DisplayName("already-verified email → silent no-op")
        void resend_AlreadyVerified_NoOp() {
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser)); // verified

            authService.resendVerification(new ResendVerificationRequest("john@example.com"));

            verify(emailVerificationCodeRepository, never()).save(any());
            verify(emailService, never()).sendRegistrationCode(anyString(), anyString());
        }

        @Test
        @DisplayName("unverified email → issues a fresh code and emails it")
        void resend_Unverified_IssuesCode() {
            User u = unverifiedUser("unverified@example.com");
            when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(u));

            authService.resendVerification(new ResendVerificationRequest("unverified@example.com"));

            verify(emailVerificationCodeRepository).invalidateAllForUser(u.getId());
            verify(emailVerificationCodeRepository).save(any(EmailVerificationCode.class));
            verify(emailService).sendRegistrationCode(eq("unverified@example.com"), anyString());
        }
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("returns session on valid credentials")
        void login_ValidCredentials_ReturnsSession() {
            LoginRequest req = new LoginRequest("john@example.com", "Password@123");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("Password@123", "$2a$12$hashed_password")).thenReturn(true);
            when(tokenProvider.createAccessToken(any(), anyString(), anyString())).thenReturn("mock-access-token");
            when(tokenProvider.createRefreshToken(any())).thenReturn("mock-refresh-token");

            LoginResponseDto response = authService.login(req);

            assertThat(response).isNotNull();
            assertThat(response.session().accessToken()).isEqualTo("mock-access-token");
            assertThat(response.user().email()).isEqualTo("john@example.com");
            assertThat(response.user().role()).isEqualTo("Customer");
        }

        @Test
        @DisplayName("unverified account → 400 EMAIL_NOT_VERIFIED")
        void login_UnverifiedEmail_ThrowsEmailNotVerified() {
            mockUser.setEmailVerified(false);
            LoginRequest req = new LoginRequest("john@example.com", "Password@123");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("Password@123", "$2a$12$hashed_password")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("EMAIL_NOT_VERIFIED");
                });
            verify(tokenProvider, never()).createAccessToken(any(), any(), any());
        }

        @Test
        @DisplayName("throws when user not found")
        void login_UserNotFound_ThrowsException() {
            LoginRequest req = new LoginRequest("notfound@example.com", "Password@123");
            when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOfSatisfying(AppException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
            verify(tokenProvider, never()).createAccessToken(any(), any(), any());
        }

        @Test
        @DisplayName("throws when password does not match")
        void login_WrongPassword_ThrowsException() {
            LoginRequest req = new LoginRequest("john@example.com", "WrongPassword");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("WrongPassword", "$2a$12$hashed_password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOfSatisfying(AppException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
            verify(tokenProvider, never()).createAccessToken(any(), any(), any());
        }
    }

    // ── Refresh token ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshAccessToken()")
    class RefreshAccessToken {

        @Test
        @DisplayName("returns new access token when refresh token is valid")
        void refreshAccessToken_ValidToken_ReturnsNewAccessToken() {
            UUID userId = mockUser.getId();
            String refreshToken = "valid-refresh-token";

            when(tokenProvider.validateRefreshToken(refreshToken)).thenReturn(true);
            when(tokenProvider.getSubjectFromRefreshToken(refreshToken)).thenReturn(userId);
            when(refreshTokenRepository.findValidToken(eq(refreshToken), eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(buildRefreshTokenEntity(userId, refreshToken)));
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(tokenProvider.createAccessToken(eq(userId), anyString(), anyString()))
                .thenReturn("new-access-token");

            String newToken = authService.refreshAccessToken(refreshToken);

            assertThat(newToken).isEqualTo("new-access-token");
        }

        @Test
        @DisplayName("throws when refresh token is invalid")
        void refreshAccessToken_InvalidToken_ThrowsException() {
            when(tokenProvider.validateRefreshToken("invalid-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshAccessToken("invalid-token"))
                .isInstanceOf(RuntimeException.class);
        }

        private RefreshToken buildRefreshTokenEntity(UUID userId, String token) {
            RefreshToken entity = new RefreshToken();
            entity.setId(UUID.randomUUID());
            entity.setUserId(userId);
            entity.setToken(token);
            entity.setExpiresAt(Instant.now().plusSeconds(604800));
            entity.setCreatedAt(Instant.now());
            return entity;
        }
    }

    // ── Forgot / reset password ──────────────────────────────────────────────────

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPassword {

        @Test
        @DisplayName("unknown email → returns null, never persists a code (anti-enumeration)")
        void forgot_UnknownEmail_ReturnsNullNoSave() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            String code = authService.forgotPassword(new ForgotPasswordRequest("ghost@example.com"));

            assertThat(code).isNull();
            verify(resetCodeRepository, never()).save(any());
            verify(emailService, never()).sendPasswordResetCode(anyString(), anyString());
        }

        @Test
        @DisplayName("privileged (admin) account → blocked, returns null, no code")
        void forgot_PrivilegedAccount_ReturnsNullNoSave() {
            mockUser.setRole("admin");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));

            String code = authService.forgotPassword(new ForgotPasswordRequest("john@example.com"));

            assertThat(code).isNull();
            verify(resetCodeRepository, never()).save(any());
            verify(emailService, never()).sendPasswordResetCode(anyString(), anyString());
        }

        @Test
        @DisplayName("customer → invalidates old codes, persists a fresh one, emails it")
        void forgot_Customer_IssuesCode() {
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));

            String code = authService.forgotPassword(new ForgotPasswordRequest("john@example.com"));

            assertThat(code).isNull(); // production env never leaks the code
            verify(resetCodeRepository).invalidateAllForUser(mockUser.getId());
            verify(resetCodeRepository).save(any(PasswordResetCode.class));
            verify(emailService).sendPasswordResetCode(eq("john@example.com"), anyString());
        }
    }

    @Nested
    @DisplayName("verifyResetCode()")
    class VerifyResetCode {

        @Test
        @DisplayName("valid code → marks it verified")
        void verifyResetCode_Valid_SetsVerified() {
            VerifyResetCodeRequest req = new VerifyResetCodeRequest("john@example.com", "123456");
            PasswordResetCode rec = PasswordResetCode.builder()
                    .userId(mockUser.getId()).code("123456")
                    .expiresAt(Instant.now().plusSeconds(300)).build();

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(resetCodeRepository.findLatestUnused(mockUser.getId(), "123456"))
                    .thenReturn(Optional.of(rec));

            authService.verifyResetCode(req);

            assertThat(rec.getVerified()).isTrue();
            verify(resetCodeRepository).save(rec);
        }

        @Test
        @DisplayName("invalid code → 400 INVALID_RESET_CODE")
        void verifyResetCode_Invalid_ThrowsBadRequest() {
            VerifyResetCodeRequest req = new VerifyResetCodeRequest("john@example.com", "000000");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(resetCodeRepository.findLatestUnused(mockUser.getId(), "000000"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyResetCode(req))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo("INVALID_RESET_CODE");
                });
        }

        @Test
        @DisplayName("expired code → 400 RESET_CODE_EXPIRED")
        void verifyResetCode_Expired_ThrowsExpired() {
            VerifyResetCodeRequest req = new VerifyResetCodeRequest("john@example.com", "123456");
            PasswordResetCode expired = PasswordResetCode.builder()
                    .userId(mockUser.getId()).code("123456")
                    .expiresAt(Instant.now().minusSeconds(1)).build();

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(resetCodeRepository.findLatestUnused(mockUser.getId(), "123456"))
                    .thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.verifyResetCode(req))
                .isInstanceOfSatisfying(AppException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("RESET_CODE_EXPIRED"));
        }
    }

    @Nested
    @DisplayName("resetPassword()")
    class ResetPassword {

        @Test
        @DisplayName("verified code → updates password and revokes all refresh tokens")
        void resetPassword_VerifiedCode_UpdatesAndRevokes() {
            ResetPasswordRequest req = new ResetPasswordRequest("john@example.com", "BrandNew@123");
            PasswordResetCode rec = PasswordResetCode.builder()
                    .userId(mockUser.getId()).code("123456").verified(true)
                    .expiresAt(Instant.now().plusSeconds(300)).build();

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(resetCodeRepository.findLatestVerifiedUnused(mockUser.getId()))
                    .thenReturn(Optional.of(rec));
            when(passwordEncoder.encode("BrandNew@123")).thenReturn("$2a$12$brandnew");

            authService.resetPassword(req);

            assertThat(mockUser.getPasswordHash()).isEqualTo("$2a$12$brandnew");
            assertThat(rec.getUsed()).isTrue();
            verify(userRepository).save(mockUser);
            verify(refreshTokenRepository).deleteAllByUserId(mockUser.getId());
        }

        @Test
        @DisplayName("no verified code → 403 forbidden")
        void resetPassword_NoVerifiedCode_ThrowsForbidden() {
            ResetPasswordRequest req = new ResetPasswordRequest("john@example.com", "BrandNew@123");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(resetCodeRepository.findLatestVerifiedUnused(mockUser.getId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOfSatisfying(AppException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
            verify(refreshTokenRepository, never()).deleteAllByUserId(any());
        }

        @Test
        @DisplayName("privileged (admin) account → 403 forbidden, even with a code")
        void resetPassword_PrivilegedAccount_ThrowsForbidden() {
            mockUser.setRole("admin");
            ResetPasswordRequest req = new ResetPasswordRequest("john@example.com", "BrandNew@123");
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOfSatisfying(AppException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("expired verified code → 400 RESET_CODE_EXPIRED")
        void resetPassword_ExpiredCode_ThrowsExpired() {
            ResetPasswordRequest req = new ResetPasswordRequest("john@example.com", "BrandNew@123");
            PasswordResetCode expired = PasswordResetCode.builder()
                    .userId(mockUser.getId()).code("123456").verified(true)
                    .expiresAt(Instant.now().minusSeconds(1)).build();

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));
            when(resetCodeRepository.findLatestVerifiedUnused(mockUser.getId()))
                    .thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOfSatisfying(AppException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("RESET_CODE_EXPIRED"));
            verify(userRepository, never()).save(any());
        }
    }
}
