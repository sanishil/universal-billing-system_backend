package com.billing.backend.repository;

import com.billing.backend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PaymentRepository — queries for the "payments" table.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    // Find all payments for a specific bill
    // SQL: SELECT * FROM payments WHERE bill_id = ?
    List<Payment> findByBillId(String billId);

    // Filter by payment status
    List<Payment> findByStatus(Payment.PaymentStatus status);

    // Filter by payment method (UPI, NET_BANKING, etc.)
    List<Payment> findByMethod(Payment.PaymentMethod method);

    // Check if a successful payment exists for this bill
    // Used to warn about duplicate payments
    boolean existsByBillIdAndStatus(String billId, Payment.PaymentStatus status);

    // Count total payments (for ID generation: PAY-0001, PAY-0002)
    long count();
}
