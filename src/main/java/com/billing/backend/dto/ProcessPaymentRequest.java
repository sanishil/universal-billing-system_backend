package com.billing.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * ProcessPaymentRequest — request body for POST /api/payments/process
 *
 * Conditional validations (upiId, bankName) are enforced in PaymentService,
 * not here, because they depend on the "method" value.
 */
@Data
public class ProcessPaymentRequest {

    @NotBlank(message = "billId is required")
    private String billId;

    private String customerName;  // optional — auto-filled from bill if not given

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "payment method is required")
    private String method;  // UPI | NET_BANKING | RUPAY_CARD | NEFT_RTGS | ...

    // Required when method = UPI (e.g. "accounts@oksbi")
    private String upiId;

    // Required when method = NET_BANKING or NEFT_RTGS
    private String bankName;
}
