package com.billing.controller;

import com.billing.backend.dto.PublicBillResponse;
import com.billing.backend.entity.Bill;
import com.billing.backend.service.BillService;
import com.billing.backend.service.InvoicePdfService;
import com.billing.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * PublicBillController — handles PUBLIC bill access (no login required).
 *
 * Used by the "Share Bill" feature:
 *   A customer receives a link like: https://app.com/bill/view/<43-char random token>
 *   They can VIEW the bill without logging in. Read-only: marking a bill as paid
 *   requires login (PATCH /api/bills/:id/mark-paid or POST /api/payments/process).
 *
 * Routes (GET only, permitted in SecurityConfig):
 *   GET /api/bills/link/:token     → view bill by share link
 *   GET /api/bill/public/:token    → same, legacy path used by the existing frontend
 *   GET /api/bills/link/:token/pdf → the invoice as a PDF download
 */
@RestController
@RequiredArgsConstructor
public class PublicBillController {

    private final BillService billService;
    private final SettingsService settingsService;
    private final InvoicePdfService invoicePdfService;

    @GetMapping({"/api/bills/link/{token}", "/api/bill/public/{token}"})
    public ResponseEntity<PublicBillResponse> getPublicBill(@PathVariable String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(PublicBillResponse.from(
                billService.getBillByUniqueLink(token),
                settingsService.getSystemSettings()));
    }

    @GetMapping("/api/bills/link/{token}/pdf")
    public ResponseEntity<byte[]> getPublicBillPdf(@PathVariable String token) {
        Bill bill = billService.getBillByUniqueLink(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("Invoice-" + bill.getId() + ".pdf").build().toString())
                .body(invoicePdfService.render(bill));
    }
}
