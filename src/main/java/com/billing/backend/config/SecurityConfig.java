package com.billing.backend.config;

import com.billing.backend.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig — configures Spring Security for JWT-based stateless auth.
 *
 * KEY CONCEPTS:
 * ─────────────
 * 1. STATELESS: No sessions. Every request must carry a valid JWT.
 *    SessionCreationPolicy.STATELESS → Spring never creates a session cookie.
 *
 * 2. PUBLIC ROUTES: Login, register, forgot-password, public bill view
 *    → accessible without a token.
 *
 * 3. PROTECTED ROUTES: Everything else requires "Authorization: Bearer <token>"
 *
 * 4. CORS: Allows the Angular frontend (localhost:4200) to call this backend.
 *
 * 5. JWT FILTER: JwtAuthFilter runs before each request to validate the token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    // Constructor injection (preferred over @Autowired)
    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * BCryptPasswordEncoder with 12 salt rounds (Rule 14).
     * @Bean → Spring registers this as a reusable bean across the app.
     *        Other services inject it via: private final PasswordEncoder passwordEncoder;
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Main security filter chain — defines which routes are public/protected.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── 1. Disable CSRF (not needed for stateless REST APIs) ──────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── 2. Enable CORS with our configuration ─────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── 3. Stateless: don't create server-side sessions ───────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── 4. Define public and protected routes ─────────────────────────
            .authorizeHttpRequests(auth -> auth

                // PUBLIC ROUTES — no JWT needed
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/auth/resend-activation",
                    "/api/auth/account-status",
                    "/api/bill/public/**"          // Public bill view + UPI pay
                ).permitAll()

                // ALL OTHER ROUTES — require valid JWT
                .anyRequest().authenticated()
            )

            // ── 5. Add our JWT filter before Spring's default auth filter ─────
            // This means: JwtAuthFilter runs first, validates the token,
            // then Spring's security layer checks the result.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration — allows the Angular frontend to call the backend.
     *
     * Without this, the browser would block all requests from localhost:4200
     * to localhost:8080 (different ports = different origins).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow requests from any origin (wildcard) or specific origins
        if ("*".equals(allowedOrigins)) {
            config.addAllowedOriginPattern("*");
        } else {
            config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        }

        // Allow all standard HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow Authorization header (for sending JWT) + Content-Type
        config.setAllowedHeaders(List.of("*"));

        // Credentials only work with explicit origins, not wildcard
        config.setAllowCredentials(!"*".equals(allowedOrigins));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);  // Apply to all /api/ routes
        return source;
    }
}
