package com.billing.backend.security;

import com.billing.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthFilter — intercepts every incoming HTTP request and validates the JWT.
 *
 * HOW IT WORKS:
 * ─────────────
 * 1. Browser sends: GET /api/bills
 *    with header: Authorization: Bearer eyJhbGci...
 *
 * 2. This filter runs BEFORE the controller.
 *    It reads the "Authorization" header.
 *
 * 3. If the token is valid:
 *    → Extract userId and role from the token
 *    → Create an "Authentication" object
 *    → Store it in SecurityContextHolder (Spring's security context for this request)
 *    → Controller runs normally
 *
 * 4. If no token or invalid token:
 *    → SecurityContextHolder stays empty
 *    → Spring Security will reject the request with 401 Unauthorized
 *      (if the route requires authentication)
 *
 * OncePerRequestFilter → guarantees this filter runs exactly once per request.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // ── Step 1: Read the Authorization header ────────────────────────────
        // Expected format: "Bearer eyJhbGci..."
        String authHeader = request.getHeader("Authorization");

        // If no Authorization header → skip JWT validation
        // (public routes like /api/auth/login don't need a token)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 2: Extract the token (remove "Bearer " prefix) ──────────────
        String token = authHeader.substring(7); // "Bearer " is 7 characters

        // ── Step 3: Validate the token ────────────────────────────────────────
        if (!jwtUtil.isTokenValid(token)) {
            // Token is expired or tampered — let the security config deny access
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 4: Extract user info from token ──────────────────────────────
        String userId = jwtUtil.extractUserId(token);
        String role   = jwtUtil.extractRole(token);

        // ── Step 5: Create Spring Security Authentication object ──────────────
        // SimpleGrantedAuthority("ROLE_Administrator") → Spring's format for roles
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,    // principal (who is authenticated) = user ID
                        null,      // credentials (not needed after auth)
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );

        // ── Step 6: Store in SecurityContextHolder ────────────────────────────
        // This tells Spring Security "this request is authenticated"
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // ── Step 7: Continue to the next filter/controller ───────────────────
        filterChain.doFilter(request, response);
    }
}
