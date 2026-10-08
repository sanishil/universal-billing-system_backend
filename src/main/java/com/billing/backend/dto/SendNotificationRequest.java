package com.billing.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendNotificationRequest {

    @NotBlank(message = "type is required (EMAIL or SMS)")
    private String type;

    @NotBlank(message = "recipient is required")
    private String recipient;

    @NotBlank(message = "message is required")
    private String message;
}
