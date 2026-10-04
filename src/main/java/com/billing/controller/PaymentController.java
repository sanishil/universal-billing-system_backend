package com.billing.controller;

import com.billing.backend.dto.ProcessPaymentRequest;
import com.billing.backend.entity.Payment;
import com.billing.backend.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PaymentController — handles payment processing and history endpoints.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ── GET /api/payments ─────────────────────────────────────────────────────
    /**
     * List all payments with optional filters.
     * ?billId=INV-2026-001
     * ?status=SUCCESS
     * ?method=UPI
     */
    @GetMapping
    public ResponseEntity<List<Payment>> getAllPayments(
            @RequestParam(required = false) String billId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String method) {
        return ResponseEntity.ok(paymentService.getAllPayments(billId, status, method));
    }

    // ── POST /api/payments/process ────────────────────────────────────────────
    @PostMapping("/process")
    public ResponseEntity<Payment> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request) {

        Payment payment = paymentService.processPayment(
                request.getBillId(),
                request.getCustomerName(),
                request.getAmount(),
                request.getMethod(),
                request.getUpiId(),
                request.getBankName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }
}
