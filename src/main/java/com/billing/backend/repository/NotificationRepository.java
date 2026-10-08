package com.billing.backend.repository;

import com.billing.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByType(Notification.NotificationType type);

    List<Notification> findByStatus(Notification.NotificationStatus status);

    long count();

    List<Notification> findAllByOrderByCreatedAtDesc();
}
