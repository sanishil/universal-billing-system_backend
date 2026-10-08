package com.billing.backend.service;

import com.billing.backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    @Value("${recaptcha.secret-key}")
    private String secretKey;

    @Value("${recaptcha.verify-url}")
    private String verifyUrl;

    @Value("${recaptcha.enabled}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public void verify(String captchaToken) {
        if (!enabled) {
            return;
        }

        if (captchaToken == null || captchaToken.isBlank()) {
            throw new BadRequestException("CAPTCHA verification is required. Please complete the reCAPTCHA.");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", secretKey);
        params.add("response", captchaToken);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(verifyUrl, params, Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                Object errorCodes = response != null ? response.get("error-codes") : "null response";
                System.err.println("[CaptchaService] reCAPTCHA failed. error-codes: " + errorCodes);
                throw new BadRequestException("CAPTCHA verification failed. Please try again.");
            }

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[CaptchaService] Error contacting reCAPTCHA API: " + e.getMessage());
            throw new BadRequestException("CAPTCHA verification could not be completed. Please try again.");
        }
    }
}
