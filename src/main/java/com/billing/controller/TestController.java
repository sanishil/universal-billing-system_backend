package com.billing.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple health-check / smoke-test controller.
 * GET /api/test → confirms the backend is running.
 */
@RestController
public class TestController {

    @GetMapping("/api/test")
    public String test() {
        return "Billing Backend Working";
    }
}