package com.billing.controller;

import com.billing.backend.dto.CreateBillRequest;
import com.billing.backend.entity.Bill;
import com.billing.backend.entity.BillEmailLog;
import com.billing.backend.entity.BillItem;
import com.billing.backend.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BillController — all bill/invoice HTTP endpoints.
 *
 * NOTE on route ordering:
 * /api/bills/stats MUST be declared BEFORE /api/bills/:id
 * Otherwise Spring would try to find a bill with id="stats".
 */
@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    // ── GET /api/bills/stats ───────────────────────────────────────────────────
    // IMPORTANT: This must come before /{id} to avoid route conflict
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getBillStats(
            Authentication authentication,
            @RequestParam(defaultValue = "sent") String scope) {
        return ResponseEntity.ok(billService.getBillStats(authentication.getName(), scope));
    }

    // ── GET /api/bills ─────────────────────────────────────────────────────────
    /**
     * List bills with optional filters.
     * ?status=PAID|PENDING|OVERDUE|ALL
     * ?search=infosys
     * ?customerId=CUST-001
     * ?scope=sent (bills you issued, default) | received (bills issued to you)
     */
    @GetMapping
    public ResponseEntity<List<Bill>> getAllBills(
            Authentication authentication,
            @RequestParam(defaultValue = "sent") String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String customerId) {

        return ResponseEntity.ok(billService.getBills(authentication.getName(), scope, status, search, customerId));
    }

    // ── GET /api/bills/:id ─────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<Bill> getBillById(Authentication authentication, @PathVariable String id) {
        return ResponseEntity.ok(billService.getBillForUser(id, authentication.getName()));
    }

    // ── POST /api/bills ────────────────────────────────────────────────────────
    /**
     * Create a new bill. All GST computation happens server-side.
     * @Valid triggers validation on CreateBillRequest + nested BillItemDto.
     */
    @PostMapping
    public ResponseEntity<Bill> createBill(
            Authentication authentication,
            @Valid @RequestBody CreateBillRequest request) {
        // Map DTO → entity objects for the service layer
        Bill billData = Bill.builder()
                .customerId(request.getCustomerId())
                .customerName(request.getCustomerName())
                .customerGstin(request.getCustomerGstin())
                .placeOfSupply(request.getPlaceOfSupply())
                .stateCode(request.getStateCode())
                .pan(request.getPan())
                .gstRate(request.getGstRate())
                .dueDate(request.getDueDate())
                .notes(request.getNotes())
                .build();

        if (request.getStatus() != null) {
            try {
                billData.setStatus(Bill.BillStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }

        // Map item DTOs → BillItem entities
        List<BillItem> items = request.getItems().stream()
                .map(dto -> BillItem.builder()
                        .name(dto.getName())
                        .hsnSac(dto.getHsnSac())
                        .quantity(dto.getQuantity())
                        .price(dto.getPrice())
                        .build())
                .collect(Collectors.toList());

        Bill created = billService.createBill(authentication.getName(), billData, items, request.isGstRateProvided(),
                request.isSendEmail(), request.isInviteRecipient());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── PUT /api/bills/:id ─────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<Bill> updateBill(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody CreateBillRequest request) {

        Bill billData = Bill.builder()
                .customerName(request.getCustomerName())
                .gstRate(request.getGstRate())
                .dueDate(request.getDueDate())
                .notes(request.getNotes())
                .build();

        if (request.getStatus() != null) {
            try {
                billData.setStatus(Bill.BillStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }

        List<BillItem> items = null;
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            items = request.getItems().stream()
                    .map(dto -> BillItem.builder()
                            .name(dto.getName())
                            .hsnSac(dto.getHsnSac())
                            .quantity(dto.getQuantity())
                            .price(dto.getPrice())
                            .build())
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(billService.updateBill(id, authentication.getName(), billData, items, request.isGstRateProvided()));
    }

    // ── DELETE /api/bills/:id ──────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteBill(Authentication authentication, @PathVariable String id) {
        billService.deleteBill(id, authentication.getName());
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ── PATCH /api/bills/:id/mark-paid ────────────────────────────────────────
    @PatchMapping("/{id}/mark-paid")
    public ResponseEntity<Bill> markPaid(Authentication authentication, @PathVariable String id) {
        return ResponseEntity.ok(billService.markAsPaid(id, authentication.getName()));
    }

    // ── POST /api/bills/:id/send-email ─────────────────────────────────────────
    // Body (all optional): { "to": "...", "cc": ["..."], "message": "..." }; to defaults to the customer
    @PostMapping("/{id}/send-email")
    public ResponseEntity<Map<String, Object>> sendEmail(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body != null ? body : Map.of();
        @SuppressWarnings("unchecked")
        List<String> cc = req.get("cc") instanceof List<?> list ? (List<String>) list : List.of();
        BillEmailLog log = billService.sendBillEmail(id, authentication.getName(),
                (String) req.get("to"), cc, (String) req.get("message"));
        return ResponseEntity.ok(Map.of("emailLogId", log.getId(), "status", log.getStatus()));
    }

    // ── GET /api/bills/:id/emails ──────────────────────────────────────────────
    @GetMapping("/{id}/emails")
    public ResponseEntity<List<BillEmailLog>> getEmails(Authentication authentication, @PathVariable String id) {
        return ResponseEntity.ok(billService.getEmailLogs(id, authentication.getName()));
    }

    // ── POST /api/bills/:id/share-link ─────────────────────────────────────────
    // Body: { "regenerate": true } makes a new link so old copies stop working
    @PostMapping("/{id}/share-link")
    public ResponseEntity<Map<String, Object>> shareLink(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean regenerate = body != null && Boolean.TRUE.equals(body.get("regenerate"));
        Map<String, Object> link = new java.util.HashMap<>();
        link.put("url", billService.shareLink(id, authentication.getName(), regenerate));
        link.put("expiresAt", null);
        return ResponseEntity.ok(link);
    }

    // ── POST /api/bills/generate-link ─────────────────────────────────────────
    // Older form of share-link: { "billId": "..." }
    @PostMapping("/generate-link")
    public ResponseEntity<Map<String, String>> generateLink(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("url", billService.shareLink(body.get("billId"), authentication.getName(), false)));
    }
}
