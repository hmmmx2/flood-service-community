package com.fyp.floodmonitoring.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Service-to-service authentication for the BFF aggregator.
 *
 * <p>The Next.js community website never exposes raw sensor coordinates
 * to the browser; it fetches them from this service server-side and
 * folds them into anonymised zones (see {@code lib/zoneAggregate.ts}).
 * That BFF call needs to bypass the user JWT system without weakening
 * it for end users.</p>
 *
 * <p>This filter accepts a single shared secret in the
 * {@code X-Internal-Key} header. When the value matches the configured
 * {@code INTERNAL_API_KEY}, the request is treated as authenticated
 * under {@code ROLE_SERVICE}. Nothing else changes — the same JWT
 * filter still runs for normal users, and unauthenticated callers
 * still hit the existing 401 handler.</p>
 *
 * <p>If {@code INTERNAL_API_KEY} is blank or unset the filter is a
 * no-op, which keeps local development friction-free; production
 * deploys must set the env var or the BFF aggregator will start
 * receiving 401s as soon as {@code /sensors} is locked down.</p>
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    /** Header name carrying the shared secret. */
    public static final String HEADER = "X-Internal-Key";

    private final String configuredKey;

    public InternalApiKeyFilter(@Value("${INTERNAL_API_KEY:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Skip entirely when no key is configured — keeps dev simple
        // and avoids accidentally locking out an environment that hasn't
        // wired the env var yet.
        if (!StringUtils.hasText(configuredKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(HEADER);
        if (StringUtils.hasText(presented)
                && SecurityContextHolder.getContext().getAuthentication() == null
                && constantTimeEquals(presented, configuredKey)) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "internal-bff",
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_SERVICE"));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Constant-time string compare. The header is supplied by a remote
     * caller so the obvious {@code String.equals()} would leak the
     * server-side secret's length through timing.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
