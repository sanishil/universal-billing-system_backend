package com.billing.controller;

import com.billing.backend.service.ReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ReportsController — serves the reports and analytics dashboards.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final ReportsService reportsService;

    // ── GET /api/reports/stats ────────────────────────────────────────────────
    // Bill counts and revenue totals
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(reportsService.getBillStats());
    }

    // ── GET /api/reports/dashboard ─────────────────────────────────────────────
    // YTD revenue, collection rate, monthly breakdown
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(reportsService.getDashboardData());
    }

    // ── GET /api/reports/analytics ─────────────────────────────────────────────
    // Payment method breakdown + bill status breakdown
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        return ResponseEntity.ok(reportsService.getAnalyticsData());
    }
}
