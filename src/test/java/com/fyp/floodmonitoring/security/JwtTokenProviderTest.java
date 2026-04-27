package com.fyp.floodmonitoring.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtTokenProvider Tests")
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    private static final String ACCESS_SECRET =
        "5a3e4587d8df1fbffdb13e6669e1ca4ad783288f74ac939316a365eb798df9bed";
    private static final String REFRESH_SECRET =
        "51029716adabb9b4da9213ab9600ecb293e458293f28bf0530ed5b9b0cfbfb30";
    private static final long ACCESS_EXPIRY_MS  = 900_000L;   // 15 min
    private static final long REFRESH_EXPIRY_MS = 604_800_000L; // 7 days

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",         ACCESS_SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtRefreshSecret",  REFRESH_SECRET);
        ReflectionTestUtils.setField(tokenProvider, "accessTokenExpiryMs",  ACCESS_EXPIRY_MS);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpiryMs", REFRESH_EXPIRY_MS);
    }

    // ── Access token ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Access Token")
    class AccessToken {

        @Test
        @DisplayName("creates a non-null, non-blank access token")
        void createAccessToken_ReturnsNonBlankToken() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createAccessToken(userId, "user@example.com", "customer");

            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("access token passes validation")
        void createAccessToken_IsValidOnValidation() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createAccessToken(userId, "user@example.com", "customer");

            assertThat(tokenProvider.validateAccessToken(token)).isTrue();
        }

        @Test
        @DisplayName("extracting subject UUID from access token returns correct userId")
        void getSubjectFromAccessToken_ReturnsCorrectUserId() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createAccessToken(userId, "user@example.com", "customer");

            UUID extracted = tokenProvider.getSubjectFromAccessToken(token);
            assertThat(extracted).isEqualTo(userId);
        }

        @Test
        @DisplayName("extracting email from access token returns correct email")
        void getEmailFromAccessToken_ReturnsCorrectEmail() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createAccessToken(userId, "user@example.com", "customer");

            String email = tokenProvider.getEmailFromAccessToken(token);
            assertThat(email).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("extracting role from access token returns correct role")
        void getRoleFromAccessToken_ReturnsCorrectRole() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createAccessToken(userId, "admin@example.com", "admin");

            String role = tokenProvider.getRoleFromAccessToken(token);
            assertThat(role).isEqualTo("admin");
        }

        @Test
        @DisplayName("tampered access token fails validation")
        void validateAccessToken_TamperedToken_ReturnsFalse() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createAccessToken(userId, "user@example.com", "customer");

            // Tamper the payload section
            String[] parts = token.split("\\.");
            String tamperedToken = parts[0] + ".TAMPERED_PAYLOAD." + parts[2];

            assertThat(tokenProvider.validateAccessToken(tamperedToken)).isFalse();
        }

        @Test
        @DisplayName("empty string fails access token validation")
        void validateAccessToken_EmptyString_ReturnsFalse() {
            assertThat(tokenProvider.validateAccessToken("")).isFalse();
        }

        @Test
        @DisplayName("null fails access token validation gracefully")
        void validateAccessToken_Null_ReturnsFalse() {
            assertThat(tokenProvider.validateAccessToken(null)).isFalse();
        }

        @Test
        @DisplayName("refresh token is rejected by access token validator")
        void validateAccessToken_RefreshToken_ReturnsFalse() {
            UUID userId = UUID.randomUUID();
            String refreshToken = tokenProvider.createRefreshToken(userId);

            // Refresh token should NOT validate as access token
            assertThat(tokenProvider.validateAccessToken(refreshToken)).isFalse();
        }
    }

    // ── Refresh token ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Refresh Token")
    class RefreshToken {

        @Test
        @DisplayName("creates a non-null, non-blank refresh token")
        void createRefreshToken_ReturnsNonBlankToken() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createRefreshToken(userId);

            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("refresh token passes validation")
        void createRefreshToken_IsValidOnValidation() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createRefreshToken(userId);

            assertThat(tokenProvider.validateRefreshToken(token)).isTrue();
        }

        @Test
        @DisplayName("extracting subject from refresh token returns correct userId")
        void getSubjectFromRefreshToken_ReturnsCorrectUserId() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createRefreshToken(userId);

            UUID extracted = tokenProvider.getSubjectFromRefreshToken(token);
            assertThat(extracted).isEqualTo(userId);
        }

        @Test
        @DisplayName("tampered refresh token fails validation")
        void validateRefreshToken_TamperedToken_ReturnsFalse() {
            UUID userId = UUID.randomUUID();
            String token = tokenProvider.createRefreshToken(userId);

            String[] parts = token.split("\\.");
            String tamperedToken = parts[0] + ".TAMPERED_PAYLOAD." + parts[2];

            assertThat(tokenProvider.validateRefreshToken(tamperedToken)).isFalse();
        }

        @Test
        @DisplayName("access token is rejected by refresh token validator")
        void validateRefreshToken_AccessToken_ReturnsFalse() {
            UUID userId = UUID.randomUUID();
            String accessToken = tokenProvider.createAccessToken(userId, "user@example.com", "customer");

            // Access token should NOT validate as refresh token
            assertThat(tokenProvider.validateRefreshToken(accessToken)).isFalse();
        }
    }

    // ── Token uniqueness ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token Uniqueness")
    class TokenUniqueness {

        @Test
        @DisplayName("two access tokens for same user are different (timestamp-based)")
        void createAccessToken_TwoTokensForSameUser_AreDifferent() throws InterruptedException {
            UUID userId = UUID.randomUUID();
            String token1 = tokenProvider.createAccessToken(userId, "user@example.com", "customer");
            Thread.sleep(1100); // JWT iat is second-precision — need >1 s to guarantee different tokens
            String token2 = tokenProvider.createAccessToken(userId, "user@example.com", "customer");

            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("access tokens for different users are different")
        void createAccessToken_DifferentUsers_ProduceDifferentTokens() {
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();

            String token1 = tokenProvider.createAccessToken(userId1, "user1@example.com", "customer");
            String token2 = tokenProvider.createAccessToken(userId2, "user2@example.com", "admin");

            assertThat(token1).isNotEqualTo(token2);
        }
    }
}
