package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.LoginRequest;
import com.fyp.floodmonitoring.dto.request.RegisterRequest;
import com.fyp.floodmonitoring.dto.response.LoginResponseDto;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.repository.RefreshTokenRepository;
import com.fyp.floodmonitoring.repository.UserRepository;
import com.fyp.floodmonitoring.repository.UserSettingRepository;
import com.fyp.floodmonitoring.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock private UserRepository         userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserSettingRepository  userSettingRepository;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private JwtTokenProvider       jwtTokenProvider;
    @Mock private EmailService           emailService;

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
        mockUser.setCreatedAt(Instant.now());
        mockUser.setUpdatedAt(Instant.now());
    }

    // ── Register ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("registers new user and returns session")
        void register_NewUser_ReturnsSession() {
            RegisterRequest req = new RegisterRequest("John", "Doe", "john@example.com", "Password@123");

            // AuthService lowercases the email before calling existsByEmail
            when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Password@123")).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenReturn(mockUser);
            when(jwtTokenProvider.createAccessToken(any(), anyString(), anyString()))
                .thenReturn("mock-access-token");
            when(jwtTokenProvider.createRefreshToken(any()))
                .thenReturn("mock-refresh-token");
            when(refreshTokenRepository.save(any())).thenReturn(null);
            // settingsService.upsertDefault is called per key — stub any invocations
            doNothing().when(userSettingRepository).upsertDefault(any(), anyString());

            LoginResponseDto response = authService.register(req);

            assertThat(response).isNotNull();
            assertThat(response.session().accessToken()).isEqualTo("mock-access-token");
            assertThat(response.session().refreshToken()).isEqualTo("mock-refresh-token");
            assertThat(response.user().email()).isEqualTo("john@example.com");

            verify(userRepository).save(any(User.class));
            verify(refreshTokenRepository).save(any());
        }

        @Test
        @DisplayName("throws exception when email already exists")
        void register_DuplicateEmail_ThrowsException() {
            RegisterRequest req = new RegisterRequest("John", "Doe", "existing@example.com", "Password@123");

            // AuthService lowercases the email before calling existsByEmail
            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(RuntimeException.class);

            verify(userRepository, never()).save(any());
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

            // AuthService lowercases the email before calling findByEmail
            when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("Password@123", "$2a$12$hashed_password"))
                .thenReturn(true);
            when(jwtTokenProvider.createAccessToken(any(), anyString(), anyString()))
                .thenReturn("mock-access-token");
            when(jwtTokenProvider.createRefreshToken(any()))
                .thenReturn("mock-refresh-token");
            when(refreshTokenRepository.save(any())).thenReturn(null);
            doNothing().when(userRepository).updateLastLogin(any(), any());

            LoginResponseDto response = authService.login(req);

            assertThat(response).isNotNull();
            assertThat(response.session().accessToken()).isEqualTo("mock-access-token");
            assertThat(response.user().email()).isEqualTo("john@example.com");
            assertThat(response.user().role()).isEqualTo("customer");
        }

        @Test
        @DisplayName("throws exception when user not found")
        void login_UserNotFound_ThrowsException() {
            LoginRequest req = new LoginRequest("notfound@example.com", "Password@123");

            // AuthService lowercases the email before calling findByEmail
            when(userRepository.findByEmail("notfound@example.com"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class);

            verify(jwtTokenProvider, never()).createAccessToken(any(), any(), any());
        }

        @Test
        @DisplayName("throws exception when password does not match")
        void login_WrongPassword_ThrowsException() {
            LoginRequest req = new LoginRequest("john@example.com", "WrongPassword");

            // AuthService lowercases the email before calling findByEmail
            when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(mockUser));
            when(passwordEncoder.matches("WrongPassword", "$2a$12$hashed_password"))
                .thenReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class);

            verify(jwtTokenProvider, never()).createAccessToken(any(), any(), any());
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

            when(jwtTokenProvider.validateRefreshToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getSubjectFromRefreshToken(refreshToken)).thenReturn(userId);
            // Service calls findValidToken(token, userId, now) — not findByToken
            when(refreshTokenRepository.findValidToken(eq(refreshToken), eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(buildRefreshTokenEntity(userId, refreshToken)));
            when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
            when(jwtTokenProvider.createAccessToken(eq(userId), anyString(), anyString()))
                .thenReturn("new-access-token");

            String newToken = authService.refreshAccessToken(refreshToken);

            assertThat(newToken).isEqualTo("new-access-token");
        }

        @Test
        @DisplayName("throws exception when refresh token is invalid")
        void refreshAccessToken_InvalidToken_ThrowsException() {
            when(jwtTokenProvider.validateRefreshToken("invalid-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshAccessToken("invalid-token"))
                .isInstanceOf(RuntimeException.class);
        }

        private com.fyp.floodmonitoring.entity.RefreshToken buildRefreshTokenEntity(UUID userId, String token) {
            com.fyp.floodmonitoring.entity.RefreshToken entity = new com.fyp.floodmonitoring.entity.RefreshToken();
            entity.setId(UUID.randomUUID());
            entity.setUserId(userId);
            entity.setToken(token);
            entity.setExpiresAt(Instant.now().plusSeconds(604800));
            entity.setCreatedAt(Instant.now());
            return entity;
        }
    }
}
