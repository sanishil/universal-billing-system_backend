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

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }

    public Notification sendNotification(String type, String recipient, String message) {
        if (!StringUtils.hasText(type)) {
            throw new BadRequestException("Notification type is required");
        }
        Notification.NotificationType notifType;
        try {
            notifType = Notification.NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("type must be EMAIL or SMS");
        }

        if (!StringUtils.hasText(recipient)) {
            throw new BadRequestException("recipient is required");
        }
        if (notifType == Notification.NotificationType.EMAIL) {
            if (!recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new BadRequestException("Invalid email address");
            }
        }
        if (notifType == Notification.NotificationType.SMS) {
            if (!recipient.matches("^\\+?[0-9]{10,15}$")) {
                throw new BadRequestException("Invalid phone number format");
            }
        }

        if (!StringUtils.hasText(message)) {
            throw new BadRequestException("message is required");
        }

        long count = notificationRepository.count();
        String notifId = "NOTIF-" + String.format("%03d", count + 1);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.ENGLISH);
        String sentAt = LocalDateTime.now().format(fmt);

        Notification.NotificationStatus status;
        try {
            if (notifType == Notification.NotificationType.EMAIL) {
                sendEmail(recipient, "Universal Billing System Notification", message);
            } else {
                sendSms(recipient, message);
            }
            status = Notification.NotificationStatus.SENT;
            log.info("Notification sent: {} to {}", notifType, recipient);
        } catch (Exception e) {
            status = Notification.NotificationStatus.FAILED;
            log.error("Failed to send {} to {}: {}", notifType, recipient, e.getMessage());
        }

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

    public void notifyBillPaid(String customerEmail, String billId, String amount) {
        String message = "Your invoice #" + billId + " for ₹" + amount +
                         " has been marked as PAID. Thank you!";
        sendNotification("EMAIL", customerEmail, message);
    }

    public void notifyBillOverdue(String customerEmail, String billId) {
        String message = "Invoice #" + billId + " is overdue. " +
                         "Please settle the payment immediately to avoid penalties.";
        sendNotification("EMAIL", customerEmail, message);
    }

    public void notifyDueSoon(String customerEmail, String billId, String dueDate) {
        String message = "Reminder: Invoice #" + billId + " is due on " + dueDate +
                         ". Please arrange payment.";
        sendNotification("EMAIL", customerEmail, message);
    }

    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(fromEmail);
        mailMessage.setTo(to);
        mailMessage.setSubject(subject);
        mailMessage.setText(body);
        mailSender.send(mailMessage);
    }

    private void sendSms(String to, String body) {
        // TODO: Integrate Twilio or MSG91 for production SMS
        log.info("[SMS STUB] To: {} | Message: {}", to, body);
    }
}
