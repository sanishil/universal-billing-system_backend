package com.billing.controller;

import com.billing.backend.entity.Bill;
import com.billing.backend.service.BillService;
import com.billing.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PublicBillController — handles PUBLIC bill access (no login required).
 *
 * Used by the "Share Bill" feature:
 *   A customer receives a link like: https://app.com/bill/view/bill-infosys-842109
 *   They can VIEW the bill and optionally pay via UPI — without logging in.
 *
 * Routes:
 *   GET  /api/bill/public/:uniqueId       → view bill by share link
 *   PATCH /api/bill/public/:uniqueId/pay  → simulate UPI payment
 */
@RestController
@RequestMapping("/api/bill/public")
@RequiredArgsConstructor
public class PublicBillController {

    private final BillService billService;
    private final PaymentService paymentService;

    // ── GET /api/bill/public/:uniqueId ────────────────────────────────────────
    /**
     * Public bill view — no JWT required.
     * The publicBillGuard on the frontend checks the uniqueId is non-empty.
     */
    @GetMapping("/{uniqueId}")
    public ResponseEntity<Bill> getPublicBill(@PathVariable String uniqueId) {
        if (uniqueId == null || uniqueId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(billService.getBillByUniqueLink(uniqueId));
    }

    // ── PATCH /api/bill/public/:uniqueId/pay ──────────────────────────────────
    /**
     * Simulate UPI payment from the public bill view.
     * Marks the bill as PAID and updates customer totalSpent.
     */
    @PatchMapping("/{uniqueId}/pay")
    public ResponseEntity<Map<String, Object>> simulatePublicPay(
            @PathVariable String uniqueId) {
        paymentService.simulatePublicPayment(uniqueId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Payment processed successfully"
        ));
    }
}
