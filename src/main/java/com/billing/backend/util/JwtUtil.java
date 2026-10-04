package com.billing.backend.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JwtUtil — creates and validates JWT tokens.
 *
 * WHAT IS JWT?
 * ────────────
 * A JWT (JSON Web Token) is a secure string that looks like:
 *   eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJVU1ItMDEiLCJuYW1lIjoiSm9obiIsImV4cCI6MTcyNX0.abc123
 *
 * It has 3 parts separated by dots:
 *   1. Header   → algorithm info (base64)
 *   2. Payload  → user data (base64) — NOT encrypted, just encoded
 *   3. Signature → proves the token wasn't tampered with
 *
 * HOW WE USE IT:
 * ─────────────
 * 1. User logs in → we create a JWT with their id, name, email, role
 * 2. We send the JWT back to the frontend
 * 3. Frontend stores it and sends it in every request:
 *    Authorization: Bearer eyJhbGci...
 * 4. Our JWT filter reads this header and validates the token
 * 5. If valid, the user is considered authenticated
 *
 * @Value reads values from application.properties
 * @Component makes Spring manage this bean
 */
@Component
public class JwtUtil {

    // Read the secret key from application.properties
    @Value("${jwt.secret}")
    private String secretString;

    // Token expiry in milliseconds (86400000 = 24 hours)
    @Value("${jwt.expiration}")
    private long expirationMs;

    /**
     * Build the cryptographic signing key from the secret string.
     * HMAC-SHA256 is used for signing — industry standard.
     */
    private Key getSigningKey() {
        // Convert the secret string to bytes for the key
        byte[] keyBytes = secretString.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a JWT token for a user after successful login.
     *
     * Token payload (claims) contains:
     *   sub   → subject = user ID (e.g. "USR-01")
     *   name  → display name
     *   email → email address
     *   role  → user role (e.g. "Administrator")
     *
     * @param userId    User's ID (stored as "sub" claim)
     * @param name      User's display name
     * @param email     User's email
     * @param role      User's role
     * @return Signed JWT string
     */
    public String generateToken(String userId, String name, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(userId)                   // "sub" claim = user ID
                .claim("name", name)                  // custom claim
                .claim("email", email)                // custom claim
                .claim("role", role)                  // custom claim
                .setIssuedAt(now)                     // "iat" = issued at time
                .setExpiration(expiry)                // "exp" = expiry time
                .signWith(getSigningKey())             // sign with HMAC-SHA256
                .compact();                           // build the final string
    }

    /**
     * Extract the user ID from a JWT token.
     * The user ID is stored in the "sub" (subject) claim.
     *
     * @param token The JWT string
     * @return The user ID (e.g. "USR-01")
     */
    public String extractUserId(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Extract the email claim from a token.
     */
    public String extractEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    /**
     * Extract the role claim from a token.
     */
    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * Check if a token is still valid (not expired, not tampered with).
     *
     * @param token The JWT string
     * @return true if valid, false if expired or invalid
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = getClaims(token);
            // Check it hasn't expired
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            // Token is malformed, expired, or has wrong signature
            return false;
        }
    }

    /**
     * Parse and return all claims from a JWT token.
     * This is the internal method used by all extraction methods.
     *
     * @throws JwtException if token is invalid, expired, or tampered
     */
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())       // use same key to verify signature
                .build()
                .parseClaimsJws(token)               // parse and verify
                .getBody();                          // return the payload (claims)
    }
}
