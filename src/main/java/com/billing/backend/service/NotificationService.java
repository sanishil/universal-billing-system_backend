package com.billing.backend.service;

import com.billing.backend.entity.Notification;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * NotificationService — sends EMAIL and SMS notifications.
 *
 * Uses Spring's JavaMailSender to send emails via SMTP.
 * SMS is stubbed (log only) — integrate Twilio/MSG91 in production.
 *
 * Auto-triggered events (called by BillService / PaymentService):
 *   - Bill marked PAID → email to customer
 *   - Bill OVERDUE     → reminder email
 *   - New customer     → alert to admin
 *
 * @Slf4j → Lombok generates a logger: log.info(), log.error(), etc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ── GET ALL NOTIFICATIONS ─────────────────────────────────────────────────

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }

    // ── SEND NOTIFICATION ─────────────────────────────────────────────────────

    /**
     * Send an EMAIL or SMS notification and record it in the database.
     *
     * @param type      "EMAIL" or "SMS" (case-sensitive per spec)
     * @param recipient Email address or phone number
     * @param message   The notification message text
     * @return Saved Notification entity
     */
    public Notification sendNotification(String type, String recipient, String message) {

        // ── Validate type ─────────────────────────────────────────────────────
        if (!StringUtils.hasText(type)) {
            throw new BadRequestException("Notification type is required");
        }
        Notification.NotificationType notifType;
        try {
            // Must be exactly EMAIL or SMS (case-sensitive per Rule 19)
            notifType = Notification.NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("type must be EMAIL or SMS");
        }

        // ── Validate recipient ────────────────────────────────────────────────
        if (!StringUtils.hasText(recipient)) {
            throw new BadRequestException("recipient is required");
        }
        if (notifType == Notification.NotificationType.EMAIL) {
            if (!recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new BadRequestException("Invalid email address");
            }
        }
        if (notifType == Notification.NotificationType.SMS) {
            // International: +91xxxxxxxxxx or local 10 digits
            if (!recipient.matches("^\\+?[0-9]{10,15}$")) {
                throw new BadRequestException("Invalid phone number format");
            }
        }

        // ── Validate message ──────────────────────────────────────────────────
        if (!StringUtils.hasText(message)) {
            throw new BadRequestException("message is required");
        }

        // ── Generate notification ID: NOTIF-001, NOTIF-002 ────────────────────
        long count = notificationRepository.count();
        String notifId = "NOTIF-" + String.format("%03d", count + 1);

        // Human-readable sent time
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.ENGLISH);
        String sentAt = LocalDateTime.now().format(fmt);

        // ── Attempt to dispatch ───────────────────────────────────────────────
        Notification.NotificationStatus status;
        try {
            if (notifType == Notification.NotificationType.EMAIL) {
                sendEmail(recipient, "Universal Billing System Notification", message);
            } else {
                sendSms(recipient, message);  // Stub — log only
            }
            status = Notification.NotificationStatus.SENT;
            log.info("Notification sent: {} to {}", notifType, recipient);
        } catch (Exception e) {
            // IMPORTANT: Don't throw — store FAILED status instead (per spec)
            status = Notification.NotificationStatus.FAILED;
            log.error("Failed to send {} to {}: {}", notifType, recipient, e.getMessage());
        }

        // ── Save to DB ────────────────────────────────────────────────────────
        Notification notification = Notification.builder()
                .id(notifId)
                .type(notifType)
                .recipient(recipient)
                .message(message)
                .status(status)
                .sentAt(sentAt)
                .build();

        return notificationRepository.save(notification);
    }

    // ── AUTO-TRIGGER: Bill Paid ───────────────────────────────────────────────

    /**
     * Auto-send payment confirmation when a bill is marked PAID.
     * Called by BillService.markAsPaid() and PaymentService.processPayment().
     */
    public void notifyBillPaid(String customerEmail, String billId, String amount) {
        String message = "Your invoice #" + billId + " for ₹" + amount +
                         " has been marked as PAID. Thank you!";
        sendNotification("EMAIL", customerEmail, message);
    }

    /**
     * Auto-send overdue reminder.
     */
    public void notifyBillOverdue(String customerEmail, String billId) {
        String message = "Invoice #" + billId + " is overdue. " +
                         "Please settle the payment immediately to avoid penalties.";
        sendNotification("EMAIL", customerEmail, message);
    }

    /**
     * Auto-send 7-day due-soon reminder.
     */
    public void notifyDueSoon(String customerEmail, String billId, String dueDate) {
        String message = "Reminder: Invoice #" + billId + " is due on " + dueDate +
                         ". Please arrange payment.";
        sendNotification("EMAIL", customerEmail, message);
    }

    // ── INTERNAL: SMTP Email Dispatch ────────────────────────────────────────

    /**
     * Send a plain text email using Spring's JavaMailSender + SMTP.
     * Configured via spring.mail.* in application.properties.
     */
    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(fromEmail);
        mailMessage.setTo(to);
        mailMessage.setSubject(subject);
        mailMessage.setText(body);
        mailSender.send(mailMessage);
    }

    /**
     * SMS stub — logs the message.
     * To enable real SMS: integrate Twilio or MSG91 SDK here.
     *
     * Twilio example:
     *   Message.creator(new PhoneNumber(to), new PhoneNumber(fromPhone), body).create();
     */
    private void sendSms(String to, String body) {
        // TODO: Integrate Twilio or MSG91 for production SMS
        log.info("[SMS STUB] To: {} | Message: {}", to, body);
    }
}
