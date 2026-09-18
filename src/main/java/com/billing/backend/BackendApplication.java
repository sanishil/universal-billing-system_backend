package com.billing.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Universal Billing System backend.
 * scanBasePackages ensures all sub-packages under com.billing are detected
 * (e.g., com.billing.controller, com.billing.backend.config, etc.)
 */
@SpringBootApplication(scanBasePackages = "com.billing")
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}