package com.billing.backend.service;

import com.billing.backend.entity.Bill;
import com.billing.backend.entity.Payment;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.BillRepository;
import com.billing.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * PaymentService — handles payment processing for all 8 payment methods.
 *
 * Payment Methods Supported:
 *   UPI, NET_BANKING, RUPAY_CARD, NEFT_RTGS,
 *   CREDIT_CARD, BANK_TRANSFER, PAYPAL, STRIPE
 *
 * On successful payment:
 *   → Bill status set to PAID (Rule 8)
 *   → Customer.totalSpent incremented
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final BillService billService;
    private final CustomerService customerService;

    // ── GET ALL PAYMENTS ──────────────────────────────────────────────────────

    /**
     * Return payments with optional filters.
     */
    public List<Payment> getAllPayments(String billId, String status, String method) {

        if (StringUtils.hasText(billId)) {
            return paymentRepository.findByBillId(billId);
        }
        if (StringUtils.hasText(status)) {
            return paymentRepository.findByStatus(parseStatus(status));
        }
        if (StringUtils.hasText(method)) {
            return paymentRepository.findByMethod(parseMethod(method));
        }
        return paymentRepository.findAll();
    }

    // ── PROCESS PAYMENT ───────────────────────────────────────────────────────

    /**
     * Process a payment for a bill.
     *
     * Steps (per specification Section 6):
     *  1.  Validate required fields
     *  2.  Validate conditional fields (upiId for UPI, bankName for NEFT)
     *  3.  Look up the bill
     *  4.  Generate payment ID: PAY-NNNN
     *  5.  Generate transaction ID: TXN-{METHOD}-{timestamp}
     *  6.  Generate UTR number
     *  7.  Set status = SUCCESS
     *  8.  Record human-readable date
     *  9.  Save payment record
     *  10. Mark bill as PAID
     *  11. Update customer totalSpent
     */
    @Transactional
    public Payment processPayment(String billId, String customerName,
                                   BigDecimal amount, String method,
                                   String upiId, String bankName) {

        // ── Validate required fields ──────────────────────────────────────────
        if (!StringUtils.hasText(billId)) {
            throw new BadRequestException("billId is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("amount must be greater than 0");
        }
        if (!StringUtils.hasText(method)) {
            throw new BadRequestException("payment method is required");
        }

        Payment.PaymentMethod paymentMethod = parseMethod(method);

        // ── Validate conditional fields ───────────────────────────────────────
        if (paymentMethod == Payment.PaymentMethod.UPI) {
            if (!StringUtils.hasText(upiId)) {
                throw new BadRequestException("upiId is required for UPI payments");
            }
            // UPI ID must end with @bankname: e.g. accounts@oksbi, john@gpay
            if (!upiId.contains("@")) {
                throw new BadRequestException(
                        "Invalid UPI ID. Must be in format: username@bankname");
            }
        }

        if (paymentMethod == Payment.PaymentMethod.NET_BANKING ||
            paymentMethod == Payment.PaymentMethod.NEFT_RTGS) {
            if (!StringUtils.hasText(bankName)) {
                throw new BadRequestException(
                        "bankName is required for " + method + " payments");
            }
        }

        // ── Look up the bill ──────────────────────────────────────────────────
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", billId));

        // ── Generate Payment ID: PAY-NNNN ─────────────────────────────────────
        long count = paymentRepository.count();
        String paymentId = "PAY-" + String.format("%04d", count + 1);

        // ── Generate Transaction ID: TXN-{METHOD}-{6-digit-timestamp} ─────────
        // Method prefix mapping from specification Section 6, Step 7
        String methodPrefix = switch (paymentMethod) {
            case UPI           -> "UPI";
            case NET_BANKING   -> "NETB";
            case RUPAY_CARD    -> "RUPA";
            case NEFT_RTGS     -> "NEFT";
            case CREDIT_CARD   -> "CRED";
            case BANK_TRANSFER -> "BANK";
            case PAYPAL        -> "PAYP";
            case STRIPE        -> "STRI";
        };
        String timestamp6 = String.valueOf(System.currentTimeMillis());
        timestamp6 = timestamp6.substring(timestamp6.length() - 6);
        String transactionId = "TXN-" + methodPrefix + "-" + timestamp6;

        // ── Generate UTR Number: UTR{10-digit-timestamp} ──────────────────────
        String timestamp10 = String.valueOf(System.currentTimeMillis());
        // Ensure we get last 10 digits
        if (timestamp10.length() > 10) {
            timestamp10 = timestamp10.substring(timestamp10.length() - 10);
        }
        String utrNumber = "UTR" + timestamp10;

        // ── Format human-readable date: "4 Oct 2026, 12:30 am" ───────────────
        // India locale for month names
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                "d MMM yyyy, hh:mm a", Locale.ENGLISH);
        String dateStr = LocalDateTime.now().format(formatter).toLowerCase();

        // ── Build payment record ──────────────────────────────────────────────
        Payment payment = Payment.builder()
                .id(paymentId)
                .billId(billId)
                .customerName(StringUtils.hasText(customerName)
                        ? customerName : bill.getCustomerName())
                .amount(amount)
                .currency("INR")
                .method(paymentMethod)
                .upiId(paymentMethod == Payment.PaymentMethod.UPI ? upiId : null)
                .bankName(StringUtils.hasText(bankName) ? bankName : null)
                .utrNumber(utrNumber)
                .status(Payment.PaymentStatus.SUCCESS)
                .transactionId(transactionId)
                .date(dateStr)
                .build();

        // ── Save payment to DB ────────────────────────────────────────────────
        Payment savedPayment = paymentRepository.save(payment);

        // ── Automatically mark the bill as PAID (Rule 8) ──────────────────────
        // Only if it's not already paid
        if (bill.getStatus() != Bill.BillStatus.PAID) {
            bill.setStatus(Bill.BillStatus.PAID);
            billRepository.save(bill);

            // Update customer.totalSpent
            customerService.addToTotalSpent(bill.getCustomerId(), bill.getTotal());
        }

        return savedPayment;
    }

    // ── SIMULATE PUBLIC UPI PAYMENT (Public Bill View) ────────────────────────

    /**
     * Simulated UPI payment from the public bill view.
     * When a customer views a shared link and clicks "Pay via UPI".
     */
    @Transactional
    public void simulatePublicPayment(String uniqueLink) {
        Bill bill = billRepository.findByUniqueLink(uniqueLink)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bill with link '" + uniqueLink + "' not found"));

        if (bill.getStatus() == Bill.BillStatus.PAID) {
            throw new BadRequestException("This bill has already been paid");
        }

        bill.setStatus(Bill.BillStatus.PAID);
        billRepository.save(bill);
        customerService.addToTotalSpent(bill.getCustomerId(), bill.getTotal());
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private Payment.PaymentMethod parseMethod(String method) {
        try {
            return Payment.PaymentMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Invalid payment method. Allowed: UPI, NET_BANKING, RUPAY_CARD, " +
                    "NEFT_RTGS, CREDIT_CARD, BANK_TRANSFER, PAYPAL, STRIPE");
        }
    }

    private Payment.PaymentStatus parseStatus(String status) {
        try {
            return Payment.PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status. Use: SUCCESS, FAILED, PENDING");
        }
    }
}
