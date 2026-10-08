package com.billing.backend.dto;

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

    private Boolean isInterState = false;

    private Integer gstRate = 18;

    @NotNull(message = "items list is required")
    @Size(min = 1, message = "Bill must have at least one item")
    @Valid
    private List<BillItemDto> items;

    private String status;
    private LocalDate dueDate;
    private String notes;

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
