package com.billing.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    // Inter-state is no longer read from the client: the server derives it from
    // the supplier state code vs. stateCode (or the customer's state code).

    /**
     * GST percentage. Explicit null = No GST.
     * Key omitted = system default rate (create) / keep current rate (update).
     */
    private BigDecimal gstRate;

    @JsonIgnore
    private boolean gstRateProvided;

    @NotNull(message = "items list is required")
    @Size(min = 1, message = "Bill must have at least one item")
    @Valid
    private List<BillItemDto> items;

    private String status;
    private LocalDate dueDate;
    private String notes;

    // Jackson only calls this when the "gstRate" key is present, even if its value is null
    public void setGstRate(BigDecimal gstRate) {
        this.gstRate = gstRate;
        this.gstRateProvided = true;
    }

    @Data
    public static class BillItemDto {

        @NotBlank(message = "Item name is required")
        private String name;

        private String hsnSac;

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
        private BigDecimal quantity;

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must be >= 0")
        private BigDecimal price;
    }
}
