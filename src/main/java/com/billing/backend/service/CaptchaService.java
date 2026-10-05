package com.billing.backend.service;

import com.billing.backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * CaptchaService — verifies a Google reCAPTCHA v2 token with Google's API.
 *
 * HOW reCAPTCHA v2 WORKS:
 * ────────────────────────
 * 1. Frontend renders the "I'm not a robot" widget (using the Site Key).
 * 2. User solves the challenge → Google gives the frontend a one-time token.
 * 3. Frontend sends that token to our backend inside the login request body.
 * 4. THIS SERVICE calls Google's siteverify API with:
 *      - our Secret Key  (proves the call is from our server)
 *      - the token       (proves the user passed the challenge)
 * 5. Google responds with { "success": true/false, ... }
 * 6. If success=false → we reject the login with 400 Bad Request.
 *
 * BYPASS IN DEV:
 * ──────────────
 * Set recaptcha.enabled=false in application.properties to skip verification
 * during local development without a real Site/Secret key pair.
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    // Injected from application.properties
    @Value("${recaptcha.secret-key}")
    private String secretKey;

    @Value("${recaptcha.verify-url}")
    private String verifyUrl;

    @Value("${recaptcha.enabled}")
    private boolean enabled;

    // RestTemplate is Spring's HTTP client for making outbound REST calls.
    // We create it inline (lightweight; no need to register as a bean here).
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Verify that the given captchaToken is valid.
     *
     * @param captchaToken The token sent by the Angular frontend widget.
     *                     Can be null/blank if the frontend didn't include it.
     * @throws BadRequestException (400) if verification fails or token is missing.
     */
    public void verify(String captchaToken) {
        // Skip verification in dev/test when recaptcha.enabled=false
        if (!enabled) {
            return;
        }

        // Token must be present
        if (captchaToken == null || captchaToken.isBlank()) {
            throw new BadRequestException("CAPTCHA verification is required. Please complete the reCAPTCHA.");
        }

        // Build the form-encoded POST body Google expects:
        //   secret=<SECRET_KEY>&response=<USER_TOKEN>
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", secretKey);
        params.add("response", captchaToken);

        // Call Google's siteverify endpoint
        // Response shape: { "success": true, "challenge_ts": "...", "hostname": "..." }
        //              or { "success": false, "error-codes": ["invalid-input-response"] }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(verifyUrl, params, Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                // Extract error codes for logging (not exposed to the client)
                Object errorCodes = response != null ? response.get("error-codes") : "null response";
                System.err.println("[CaptchaService] reCAPTCHA failed. error-codes: " + errorCodes);
                throw new BadRequestException("CAPTCHA verification failed. Please try again.");
            }

        } catch (BadRequestException e) {
            // Re-throw our own exception as-is
            throw e;
        } catch (Exception e) {
            // Network/timeout error calling Google — fail safe (reject the request)
            System.err.println("[CaptchaService] Error contacting reCAPTCHA API: " + e.getMessage());
            throw new BadRequestException("CAPTCHA verification could not be completed. Please try again.");
        }
    }
}
