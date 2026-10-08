package com.billing.backend.repository;

import com.billing.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    List<Payment> findByBillId(String billId);

    List<Payment> findByStatus(Payment.PaymentStatus status);

    List<Payment> findByMethod(Payment.PaymentMethod method);

    boolean existsByBillIdAndStatus(String billId, Payment.PaymentStatus status);

    long count();
}
