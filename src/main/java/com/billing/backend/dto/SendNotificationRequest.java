package com.billing.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SendNotificationRequest — request body for POST /api/notifications/send
 */
@Data
public class SendNotificationRequest {

    @NotBlank(message = "type is required (EMAIL or SMS)")
    private String type;       // "EMAIL" or "SMS" — case-sensitive per Rule 19

    @NotBlank(message = "recipient is required")
    private String recipient;  // email address OR phone number

    @NotBlank(message = "message is required")
    private String message;
}
