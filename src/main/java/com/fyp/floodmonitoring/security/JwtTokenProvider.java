package com.fyp.floodmonitoring.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Stateless JWT token provider.
 *
 * <p>Access tokens: 15 min, signed with {@code app.jwt.secret}.
 * Refresh tokens: 7 days, signed with {@code app.jwt.refresh-secret}.
 * Both tokens embed the user UUID as the {@code sub} claim.</p>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.refresh-secret}")
    private String jwtRefreshSecret;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    // ── Token creation ────────────────────────────────────────────────────────

    public String createAccessToken(UUID userId, String email, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(getSigningKey(jwtSecret))
                .compact();
    }

    public String createRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiryMs))
                .signWith(getSigningKey(jwtRefreshSecret))
                .compact();
    }

    // ── Token validation ──────────────────────────────────────────────────────

    public boolean validateAccessToken(String token) {
        return validate(token, jwtSecret);
    }

    public boolean validateRefreshToken(String token) {
        return validate(token, jwtRefreshSecret);
    }

    private boolean validate(String token, String secret) {
        try {
            Jwts.parser().verifyWith(getSigningKey(secret)).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: {}", ex.getMessage());
            return false;
        } catch (JwtException ex) {
            // Malformed / wrong-signature tokens — downgraded to DEBUG so unauthenticated
            // requests (empty Bearer header, health checks, etc.) don't flood WARN logs.
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        } catch (IllegalArgumentException ex) {
            // Blank or null token string — completely expected for unauthenticated requests.
            log.debug("JWT token is blank or null: {}", ex.getMessage());
            return false;
        }
    }

    // ── Claim extraction ──────────────────────────────────────────────────────

    public UUID getSubjectFromAccessToken(String token) {
        return UUID.fromString(parseClaims(token, jwtSecret).getSubject());
    }

    public UUID getSubjectFromRefreshToken(String token) {
        return UUID.fromString(parseClaims(token, jwtRefreshSecret).getSubject());
    }

    public String getEmailFromAccessToken(String token) {
        return parseClaims(token, jwtSecret).get("email", String.class);
    }

    public String getRoleFromAccessToken(String token) {
        return parseClaims(token, jwtSecret).get("role", String.class);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Claims parseClaims(String token, String secret) {
        return Jwts.parser()
                .verifyWith(getSigningKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey(String secret) {
        // Secrets are generated as hex strings (64+ hex chars = 256+ bits).
        // Fall back to raw UTF-8 bytes if the string is not valid Base64.
        if (secret != null && secret.matches("[0-9a-fA-F]+") && secret.length() >= 64) {
            // Decode as hex
            byte[] bytes = new byte[secret.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(secret.substring(i * 2, i * 2 + 2), 16);
            }
            return Keys.hmacShaKeyFor(bytes);
        }
        // Fall back: try Base64, then raw UTF-8
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (Exception e) {
            return Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
