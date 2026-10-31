package com.billing.controller;

import com.billing.backend.entity.Customer;
import com.billing.backend.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CustomerController — handles all customer CRUD HTTP endpoints.
 *
 * All routes here are protected by JWT (configured in SecurityConfig).
 * The JWT filter runs before this controller and validates the token.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    // ── GET /api/customers ────────────────────────────────────────────────────
    /**
     * Paginated customer list with optional search and status filter.
     *
     * Query params:
     *   ?search=infosys        → partial name/email search
     *   ?status=ACTIVE         → filter by ACTIVE or INACTIVE
     *   ?page=0&size=10        → page number (0-based) and page size (max 100)
     *
     * @RequestParam(required = false) → parameter is optional (null if not sent)
     */
    @GetMapping
    public ResponseEntity<Page<Customer>> getCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Customer.CustomerStatus statusEnum = null;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                statusEnum = Customer.CustomerStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return ResponseEntity.ok(customerService.getCustomers(search, statusEnum, page, size));
    }

    // ── GET /api/customers/:id ─────────────────────────────────────────────────
    /**
     * @PathVariable → extracts "id" from the URL path, e.g. /api/customers/CUST-001
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable String id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    // ── POST /api/customers ────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Customer> createCustomer(@RequestBody Customer customerData) {
        Customer created = customerService.createCustomer(customerData);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── PUT /api/customers/:id ─────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(
            @PathVariable String id,
            @RequestBody Customer updatedData) {
        return ResponseEntity.ok(customerService.updateCustomer(id, updatedData));
    }

    // ── DELETE /api/customers/:id ──────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCustomer(@PathVariable String id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
