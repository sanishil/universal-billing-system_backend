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

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final BillService billService;
    private final CustomerService customerService;

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

    @Transactional
    public Payment processPayment(String billId, String customerName,
                                   BigDecimal amount, String method,
                                   String upiId, String bankName) {

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

        if (paymentMethod == Payment.PaymentMethod.UPI) {
            if (!StringUtils.hasText(upiId)) {
                throw new BadRequestException("upiId is required for UPI payments");
            }
            if (!upiId.contains("@")) {
                throw new BadRequestException("Invalid UPI ID. Must be in format: username@bankname");
            }
        }

        if (paymentMethod == Payment.PaymentMethod.NET_BANKING ||
            paymentMethod == Payment.PaymentMethod.NEFT_RTGS) {
            if (!StringUtils.hasText(bankName)) {
                throw new BadRequestException("bankName is required for " + method + " payments");
            }
        }

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", billId));

        long count = paymentRepository.count();
        String paymentId = "PAY-" + String.format("%04d", count + 1);

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

        String timestamp10 = String.valueOf(System.currentTimeMillis());
        if (timestamp10.length() > 10) {
            timestamp10 = timestamp10.substring(timestamp10.length() - 10);
        }
        String utrNumber = "UTR" + timestamp10;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.ENGLISH);
        String dateStr = LocalDateTime.now().format(formatter).toLowerCase();

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

        Payment savedPayment = paymentRepository.save(payment);

        if (bill.getStatus() != Bill.BillStatus.PAID) {
            bill.setStatus(Bill.BillStatus.PAID);
            billRepository.save(bill);

            customerService.addToTotalSpent(bill.getCustomerId(), bill.getTotal());
        }

        return savedPayment;
    }

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
