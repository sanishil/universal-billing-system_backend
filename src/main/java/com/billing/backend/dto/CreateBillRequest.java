package com.billing.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * CreateBillRequest — request body for POST /api/bills
 *
 * Contains all the data a user submits when creating a new invoice.
 * The backend ignores any submitted totals/taxes and recomputes them.
 *
 * @Valid on the items list triggers validation of each BillItemDto inside.
 */
@Data
public class CreateBillRequest {

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotBlank(message = "customerName is required")
    private String customerName;

    private String customerGstin;
    private String placeOfSupply;
    private String stateCode;
    private String pan;

    // true = inter-state (IGST), false = intra-state (CGST + SGST)
    private Boolean isInterState = false;

    // Must be 0, 5, 12, 18, or 28 — validated in BillService
    private Integer gstRate = 18;

    // @Valid ensures each item inside the list is also validated
    @NotNull(message = "items list is required")
    @Size(min = 1, message = "Bill must have at least one item")
    @Valid
    private List<BillItemDto> items;

    private String status;   // PENDING, PAID, OVERDUE — optional, defaults to PENDING
    private LocalDate dueDate; // optional, defaults to today + 14 days
    private String notes;    // optional

    /**
     * Inner DTO for a single line item in the bill.
     * Each item is validated independently.
     */
    @Data
    public static class BillItemDto {

        @NotBlank(message = "Item name is required")
        private String name;

        // HSN/SAC code — defaults to "998314" if not provided
        private String hsnSac;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
        private BigDecimal quantity;

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must be >= 0")
        private BigDecimal price;
    }
}
