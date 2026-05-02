package com.fyp.floodmonitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.request.LoginRequest;
import com.fyp.floodmonitoring.dto.request.RegisterRequest;
import com.fyp.floodmonitoring.dto.request.ForgotPasswordRequest;
import com.fyp.floodmonitoring.dto.request.VerifyResetCodeRequest;
import com.fyp.floodmonitoring.dto.request.ResetPasswordRequest;
import com.fyp.floodmonitoring.dto.response.AuthSessionDto;
import com.fyp.floodmonitoring.dto.response.LoginResponseDto;
import com.fyp.floodmonitoring.dto.response.UserSummaryDto;
import com.fyp.floodmonitoring.service.AuthService;
import com.fyp.floodmonitoring.config.SecurityConfig;
import com.fyp.floodmonitoring.config.TestSecurityConfig;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = AuthController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@Import(TestSecurityConfig.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private AuthService authService;

    private LoginResponseDto mockLoginResponse;

    @BeforeEach
    void setUp() {
        AuthSessionDto session = new AuthSessionDto(
            "mock-access-token", "mock-refresh-token", "2099-01-01T00:00:00Z"
        );
        UserSummaryDto user = new UserSummaryDto(
            "user-uuid", "test@example.com", "Test", "User", "Test User", null, "Customer"
        );
        mockLoginResponse = new LoginResponseDto(session, user);
    }

    // ── POST /auth/register ────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        @DisplayName("returns 201 with session on valid registration")
        void register_ValidRequest_Returns201() throws Exception {
            when(authService.register(any(RegisterRequest.class))).thenReturn(mockLoginResponse);

            RegisterRequest req = new RegisterRequest("John", "Doe", "john@example.com", "Password@123");

            mockMvc.perform(post("/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.session.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
        }

        @Test
        @DisplayName("returns 400 when email is blank")
        void register_BlankEmail_Returns400() throws Exception {
            RegisterRequest req = new RegisterRequest("John", "Doe", "", "Password@123");

            mockMvc.perform(post("/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when password is too short")
        void register_ShortPassword_Returns400() throws Exception {
            RegisterRequest req = new RegisterRequest("John", "Doe", "john@example.com", "short");

            mockMvc.perform(post("/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when email format is invalid")
        void register_InvalidEmailFormat_Returns400() throws Exception {
            RegisterRequest req = new RegisterRequest("John", "Doe", "notanemail", "Password@123");

            mockMvc.perform(post("/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── POST /auth/login ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("returns 200 with session on valid credentials")
        void login_ValidCredentials_Returns200() throws Exception {
            when(authService.login(any(LoginRequest.class))).thenReturn(mockLoginResponse);

            LoginRequest req = new LoginRequest("test@example.com", "Password@123");

            mockMvc.perform(post("/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.accessToken").exists())
                .andExpect(jsonPath("$.session.refreshToken").exists())
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.role").value("Customer"));
        }

        @Test
        @DisplayName("returns 400 when email is blank")
        void login_BlankEmail_Returns400() throws Exception {
            LoginRequest req = new LoginRequest("", "Password@123");

            mockMvc.perform(post("/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when password is blank")
        void login_BlankPassword_Returns400() throws Exception {
            LoginRequest req = new LoginRequest("test@example.com", "");

            mockMvc.perform(post("/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("propagates exception from service (e.g. bad credentials)")
        void login_ServiceThrowsException_Propagates() throws Exception {
            when(authService.login(any())).thenThrow(new RuntimeException("Invalid credentials"));

            LoginRequest req = new LoginRequest("test@example.com", "wrongpass");

            mockMvc.perform(post("/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError());
        }
    }

    // ── POST /auth/refresh ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/refresh")
    class Refresh {

        @Test
        @DisplayName("returns 200 with new accessToken on valid refreshToken")
        void refresh_ValidToken_Returns200() throws Exception {
            when(authService.refreshAccessToken("valid-refresh-token")).thenReturn("new-access-token");

            mockMvc.perform(post("/auth/refresh")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("refreshToken", "valid-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
        }
    }

    // ── POST /auth/forgot-password ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/forgot-password")
    class ForgotPassword {

        @Test
        @DisplayName("returns 200 with message on valid email")
        void forgotPassword_ValidEmail_Returns200() throws Exception {
            when(authService.forgotPassword(any())).thenReturn("123456");

            ForgotPasswordRequest req = new ForgotPasswordRequest("test@example.com");

            mockMvc.perform(post("/auth/forgot-password")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("returns 400 when email is blank")
        void forgotPassword_BlankEmail_Returns400() throws Exception {
            ForgotPasswordRequest req = new ForgotPasswordRequest("");

            mockMvc.perform(post("/auth/forgot-password")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── POST /auth/verify-reset-code ───────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/verify-reset-code")
    class VerifyResetCode {

        @Test
        @DisplayName("returns 200 on valid code")
        void verifyResetCode_ValidCode_Returns200() throws Exception {
            doNothing().when(authService).verifyResetCode(any());

            VerifyResetCodeRequest req = new VerifyResetCodeRequest("test@example.com", "123456");

            mockMvc.perform(post("/auth/verify-reset-code")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Code verified successfully"));
        }
    }

    // ── POST /auth/reset-password ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/reset-password")
    class ResetPassword {

        @Test
        @DisplayName("returns 200 on successful password reset")
        void resetPassword_ValidRequest_Returns200() throws Exception {
            doNothing().when(authService).resetPassword(any());

            ResetPasswordRequest req = new ResetPasswordRequest("test@example.com", "NewPassword@123");

            mockMvc.perform(post("/auth/reset-password")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully"));
        }

        @Test
        @DisplayName("returns 400 when new password is too short")
        void resetPassword_ShortPassword_Returns400() throws Exception {
            ResetPasswordRequest req = new ResetPasswordRequest("test@example.com", "short");

            mockMvc.perform(post("/auth/reset-password")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }
}
