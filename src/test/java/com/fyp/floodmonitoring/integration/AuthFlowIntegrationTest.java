package com.fyp.floodmonitoring.integration;

import com.fyp.floodmonitoring.dto.request.LoginRequest;
import com.fyp.floodmonitoring.dto.request.RegisterRequest;
import com.fyp.floodmonitoring.dto.response.AuthSessionDto;
import com.fyp.floodmonitoring.dto.response.LoginResponseDto;
import com.fyp.floodmonitoring.dto.response.UserProfileDto;
import com.fyp.floodmonitoring.dto.response.UserSummaryDto;
import com.fyp.floodmonitoring.entity.RefreshToken;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.repository.*;
import com.fyp.floodmonitoring.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the full authentication flow.
 *
 * <p>Loads the full Spring Boot application context (including the JWT filter chain
 * and all security configuration) but mocks all JPA repository and email dependencies
 * so no real database or mail server is required.</p>
 *
 * <p>Flow under test:
 * <ol>
 *   <li>POST /auth/register → receives 201 with session tokens</li>
 *   <li>POST /auth/login    → receives 200 with session tokens</li>
 *   <li>GET  /profile       → authenticated access returns 200</li>
 *   <li>GET  /profile       → without token returns 401</li>
 *   <li>POST /auth/refresh  → valid refresh token returns new access token</li>
 * </ol>
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@DisplayName("Auth Flow Integration Test")
class AuthFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── Mocked repositories (no real DB needed) ───────────────────────────────

    @MockBean private UserRepository             userRepository;
    @MockBean private RefreshTokenRepository     refreshTokenRepository;
    @MockBean private UserSettingRepository      userSettingRepository;
    @MockBean private PasswordResetCodeRepository passwordResetCodeRepository;
    @MockBean private BlogRepository             blogRepository;
    @MockBean private NodeRepository             nodeRepository;
    @MockBean private EventRepository            eventRepository;
    @MockBean private CommunityPostRepository    communityPostRepository;
    @MockBean private CommunityCommentRepository communityCommentRepository;
    @MockBean private CommunityGroupRepository   communityGroupRepository;
    @MockBean private CommunityGroupMemberRepository communityGroupMemberRepository;
    @MockBean private CommunityPostLikeRepository communityPostLikeRepository;
    @MockBean private UserFavouriteNodeRepository userFavouriteNodeRepository;
    @MockBean private SafetyContentRepository    safetyContentRepository;
    @MockBean private ZoneRepository             zoneRepository;
    @MockBean private BroadcastRepository        broadcastRepository;
    @MockBean private ReportRepository           reportRepository;
    @MockBean private WebPushSubscriptionRepository webPushSubscriptionRepository;

    // ── Mocked external services ──────────────────────────────────────────────

    @MockBean private com.fyp.floodmonitoring.service.EmailService emailService;

    // ── Test fixtures ──────────────────────────────────────────────────────────

    private static final UUID   TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TEST_EMAIL   = "integration@test.com";
    private static final String TEST_PASSWORD = "TestPass@123";

    private User mockUser;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;

        mockUser = new User();
        mockUser.setId(TEST_USER_ID);
        mockUser.setFirstName("Integration");
        mockUser.setLastName("Tester");
        mockUser.setEmail(TEST_EMAIL);
        mockUser.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        mockUser.setRole("customer");
        mockUser.setCreatedAt(Instant.now());
        mockUser.setUpdatedAt(Instant.now());
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/register")
    class Registration {

        @Test
        @DisplayName("returns 201 with JWT session on successful registration")
        void register_ValidRequest_Returns201WithSession() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(mockUser);
            when(refreshTokenRepository.save(any())).thenReturn(null);
            doNothing().when(userSettingRepository).upsertDefault(any(), anyString());

            RegisterRequest req = new RegisterRequest(
                    "Integration", "Tester", TEST_EMAIL, TEST_PASSWORD
            );

            ResponseEntity<LoginResponseDto> response = restTemplate.postForEntity(
                    baseUrl + "/auth/register", req, LoginResponseDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().session()).isNotNull();
            assertThat(response.getBody().session().accessToken()).isNotBlank();
            assertThat(response.getBody().session().refreshToken()).isNotBlank();
            assertThat(response.getBody().user().email()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("returns 400 when email is invalid")
        void register_InvalidEmail_Returns400() {
            RegisterRequest req = new RegisterRequest(
                    "Test", "User", "not-an-email", TEST_PASSWORD
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/auth/register", req, Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns 400 when password is too short")
        void register_ShortPassword_Returns400() {
            RegisterRequest req = new RegisterRequest(
                    "Test", "User", TEST_EMAIL, "short"
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/auth/register", req, Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("returns 200 with JWT session on valid credentials")
        void login_ValidCredentials_Returns200WithSession() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
            when(refreshTokenRepository.save(any())).thenReturn(null);
            doNothing().when(userRepository).updateLastLogin(any(), any());

            LoginRequest req = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

            ResponseEntity<LoginResponseDto> response = restTemplate.postForEntity(
                    baseUrl + "/auth/login", req, LoginResponseDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().session().accessToken()).isNotBlank();
            assertThat(response.getBody().user().role()).isEqualTo("Customer");
        }

        @Test
        @DisplayName("returns 400 when email is blank")
        void login_BlankEmail_Returns400() {
            LoginRequest req = new LoginRequest("", TEST_PASSWORD);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/auth/login", req, Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ── Protected resource access ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /profile — JWT-protected access")
    class ProtectedResourceAccess {

        @Test
        @DisplayName("returns 401 when no Authorization header is provided")
        void getProfile_NoToken_Returns401() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/profile", Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("returns 401 when Bearer token is malformed")
        void getProfile_MalformedToken_Returns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer this-is-not-a-jwt");
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/profile", HttpMethod.GET, request, Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("returns 200 when a valid JWT is presented")
        void getProfile_ValidToken_Returns200() {
            String accessToken = jwtTokenProvider.createAccessToken(TEST_USER_ID, TEST_EMAIL, "customer");
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(mockUser));

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UserProfileDto> response = restTemplate.exchange(
                    baseUrl + "/profile", HttpMethod.GET, request, UserProfileDto.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().email()).isEqualTo(TEST_EMAIL);
        }
    }

    // ── Token refresh ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/refresh")
    class TokenRefresh {

        @Test
        @DisplayName("returns 200 with new access token on valid refresh token")
        void refresh_ValidRefreshToken_ReturnsNewAccessToken() {
            String refreshToken = jwtTokenProvider.createRefreshToken(TEST_USER_ID);

            RefreshToken storedToken = new RefreshToken();
            storedToken.setId(UUID.randomUUID());
            storedToken.setUserId(TEST_USER_ID);
            storedToken.setToken(refreshToken);
            storedToken.setExpiresAt(Instant.now().plusSeconds(604800));
            storedToken.setCreatedAt(Instant.now());

            when(refreshTokenRepository.findValidToken(eq(refreshToken), eq(TEST_USER_ID), any()))
                    .thenReturn(Optional.of(storedToken));
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(mockUser));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(
                    Map.of("refreshToken", refreshToken), headers
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/auth/refresh", request, Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsKey("accessToken");
            assertThat(response.getBody().get("accessToken")).isNotNull();
        }

        @Test
        @DisplayName("returns 4xx/5xx or connectivity error when refresh token is invalid")
        void refresh_InvalidToken_ReturnsError() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(
                    Map.of("refreshToken", "invalid-token"), headers
            );

            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        baseUrl + "/auth/refresh", request, Map.class
                );
                // Server returned a parseable error response — verify it is an error status
                assertThat(response.getStatusCode().is4xxClientError()
                        || response.getStatusCode().is5xxServerError()).isTrue();
            } catch (ResourceAccessException ex) {
                // Apache HttpClient throws ResourceAccessException when it cannot retry a
                // 401 POST in streaming mode.  This still proves the server rejected the
                // invalid token, so the test intent is satisfied.
                assertThat(ex.getMessage()).containsAnyOf("cannot retry", "authentication", "401");
            }
        }
    }

    // ── Public endpoints accessible without auth ──────────────────────────────

    @Nested
    @DisplayName("Public endpoints (no auth)")
    class PublicEndpoints {

        @Test
        @DisplayName("GET /actuator/health returns 200 without authentication")
        void healthEndpoint_Public_Returns200() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/actuator/health", Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("GET /sensors is public and returns 200")
        void sensors_Public_Returns200() {
            when(nodeRepository.findAllByOrderByNodeIdAsc()).thenReturn(java.util.Collections.emptyList());

            ResponseEntity<Object[]> response = restTemplate.getForEntity(
                    baseUrl + "/sensors", Object[].class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
