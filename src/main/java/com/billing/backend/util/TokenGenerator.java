package com.billing.backend.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Unguessable tokens for public bill links.
 * 32 random bytes → 43-char Base64URL string (no padding).
 */
@Component
public class TokenGenerator {

    private final SecureRandom random = new SecureRandom();

    public String urlSafe(int numBytes) {
        byte[] bytes = new byte[numBytes];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
