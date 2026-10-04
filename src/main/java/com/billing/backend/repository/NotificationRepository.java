package com.billing.backend.repository;

import com.billing.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * NotificationRepository — queries for the "notifications" table.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    // Filter by notification type (EMAIL or SMS)
    List<Notification> findByType(Notification.NotificationType type);

    // Filter by status (SENT, FAILED, PENDING)
    List<Notification> findByStatus(Notification.NotificationStatus status);

    // Count total notifications (used for ID generation: NOTIF-001, NOTIF-002)
    long count();

    // Find all notifications sorted by created time (newest first)
    // Spring reads "OrderByCreatedAtDesc" and generates ORDER BY created_at DESC
    List<Notification> findAllByOrderByCreatedAtDesc();
}
