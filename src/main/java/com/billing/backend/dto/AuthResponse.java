package com.billing.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AuthResponse — the response body for login and register.
 *
 * Returned after successful login or registration.
 * The frontend stores the "user" object in sessionStorage as "ubs_session_user".
 *
 * Example JSON:
 * {
 *   "token": "eyJhbGci...",
 *   "user": {
 *     "id": "USR-01",
 *     "name": "Administrator",
 *     "email": "admin@universalbilling.io",
 *     "role": "Administrator",
 *     "avatarUrl": null
 *   }
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private UserDto user;

    /**
     * Inner DTO: matches the User interface in the Angular frontend (auth.service.ts).
     * Only includes fields the frontend needs — no passwordHash exposed!
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private String id;
        private String name;
        private String email;
        private String role;
        private String avatarUrl;  // nullable
    }
}
