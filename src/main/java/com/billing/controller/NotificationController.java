package com.billing.controller;

import com.billing.backend.dto.SendNotificationRequest;
import com.billing.backend.entity.Notification;
import com.billing.backend.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NotificationController — handles notification history and sending.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // ── GET /api/notifications ────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }

    // ── POST /api/notifications/send ──────────────────────────────────────────
    @PostMapping("/send")
    public ResponseEntity<Notification> sendNotification(
            @Valid @RequestBody SendNotificationRequest request) {

        Notification notification = notificationService.sendNotification(
                request.getType(),
                request.getRecipient(),
                request.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }
}
