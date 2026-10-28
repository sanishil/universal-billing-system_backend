package com.billing.controller;

import com.billing.backend.dto.CreateBillRequest;
import com.billing.backend.entity.Bill;
import com.billing.backend.entity.BillItem;
import com.billing.backend.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    // Frontend URL used in share links (must be a single URL, not the CORS list)
    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    // ── GET /api/bills/stats ───────────────────────────────────────────────────
    // IMPORTANT: This must come before /{id} to avoid route conflict
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getBillStats() {
        return ResponseEntity.ok(billService.getBillStats());
    }

    // ── GET /api/bills ─────────────────────────────────────────────────────────
    /**
     * List bills with optional filters.
     * ?status=PAID|PENDING|OVERDUE|ALL
     * ?search=infosys
     * ?customerId=CUST-001
     */
    @GetMapping
    public ResponseEntity<List<Bill>> getAllBills(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String customerId) {

        return ResponseEntity.ok(billService.getAllBills(status, search, customerId));
    }

    // ── GET /api/bills/:id ─────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<Bill> getBillById(@PathVariable String id) {
        return ResponseEntity.ok(billService.getBillById(id));
    }

    // ── POST /api/bills ────────────────────────────────────────────────────────
    /**
     * Create a new bill. All GST computation happens server-side.
     * @Valid triggers validation on CreateBillRequest + nested BillItemDto.
     */
    @PostMapping
    public ResponseEntity<Bill> createBill(@Valid @RequestBody CreateBillRequest request) {
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

        Bill created = billService.createBill(billData, items, request.isGstRateProvided());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── PUT /api/bills/:id ─────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<Bill> updateBill(
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

        return ResponseEntity.ok(billService.updateBill(id, billData, items, request.isGstRateProvided()));
    }

    // ── DELETE /api/bills/:id ──────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteBill(@PathVariable String id) {
        billService.deleteBill(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ── PATCH /api/bills/:id/mark-paid ────────────────────────────────────────
    @PatchMapping("/{id}/mark-paid")
    public ResponseEntity<Bill> markPaid(@PathVariable String id) {
        return ResponseEntity.ok(billService.markAsPaid(id));
    }

    // ── POST /api/bills/generate-link ─────────────────────────────────────────
    @PostMapping("/generate-link")
    public ResponseEntity<Map<String, String>> generateLink(
            @RequestBody Map<String, String> body) {
        String billId = body.get("billId");
        String shareUrl = billService.generateShareLink(billId, publicBaseUrl);
        return ResponseEntity.ok(Map.of("url", shareUrl));
    }
}
