package com.fyp.floodmonitoring.config;

import com.fyp.floodmonitoring.security.InternalApiKeyFilter;
import com.fyp.floodmonitoring.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration.
 *
 * <p>Stateless JWT authentication — no sessions, no CSRF.
 * Public endpoints: /auth/** and /health (actuator).
 * Everything else requires a valid Bearer token.</p>
 *
 * // TODO: Consider adding /api/v1 prefix for versioning in next major release
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless REST APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session — no HttpSession created
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Route authorisation rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,
                    "/auth/login",
                    "/auth/register",
                    "/auth/refresh",
                    "/auth/forgot-password",
                    "/auth/verify-reset-code",
                    "/auth/reset-password",
                    "/auth/verify-email",
                    "/auth/resend-verification",
                    "/ingest").permitAll()         // IoT devices — API-key validated in controller
                .requestMatchers(HttpMethod.GET,
                    "/blogs",
                    "/blogs/**",
                    "/safety",
                    "/safety/**",
                    "/community/posts",
                    "/community/posts/**",
                    "/community/groups",
                    "/community/groups/**",
                    "/flood-alerts",
                    "/flood-alerts/active").permitAll()  // public read
                // Sensor coordinates are sensitive (each node is expensive
                // hardware). The Next.js BFF aggregates them into zones
                // before serving to browsers; the BFF authenticates with
                // a shared X-Internal-Key header (see InternalApiKeyFilter).
                // Internal admin tools also reach /sensors via JWT.
                .requestMatchers(HttpMethod.GET,
                    "/sensors",
                    "/sensors/**").authenticated()
                .requestMatchers("/actuator/health/**").permitAll()
                // SSE for sensor streams carries the same coords as the
                // HTTP endpoint — same rule.
                .requestMatchers("/sse/sensors", "/sse/sensors/**").authenticated()
                // Other SSE channels (community notifications, etc.)
                // stay open — they don't expose location data.
                .requestMatchers("/sse/**").permitAll()
                .anyRequest().authenticated()
            )

            // Structured JSON error responses for auth failures
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setContentType("application/json");
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setContentType("application/json");
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
                })
            )

            // JWT filter runs before the username/password filter.
            // The internal-key filter runs BEFORE the JWT filter so that
            // a service caller (the Next.js BFF) authenticates without
            // ever touching the user-session machinery.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(internalApiKeyFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
