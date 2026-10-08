package com.billing.backend.service;

import com.billing.backend.entity.Bill;
import com.billing.backend.repository.BillRepository;
import com.billing.backend.repository.CustomerRepository;
import com.billing.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportsService {

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    private static final String[] MONTH_NAMES = {
        "", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    public Map<String, Object> getBillStats() {
        BigDecimal totalRevenue = billRepository.getTotalRevenue();
        long paidCount    = billRepository.countByStatus(Bill.BillStatus.PAID);
        long pendingCount = billRepository.countByStatus(Bill.BillStatus.PENDING);
        long overdueCount = billRepository.countByStatus(Bill.BillStatus.OVERDUE);
        long totalCount   = billRepository.count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRevenue",  totalRevenue);
        stats.put("paidCount",     paidCount);
        stats.put("pendingCount",  pendingCount);
        stats.put("overdueCount",  overdueCount);
        stats.put("totalCount",    totalCount);
        return stats;
    }

    public Map<String, Object> getDashboardData() {
        int currentYear = LocalDate.now().getYear();

        BigDecimal ytdRevenue = billRepository.getTotalRevenueByYear(currentYear);

        long totalBills = billRepository.count();

        long paidCount  = billRepository.countByStatus(Bill.BillStatus.PAID);
        double collectionRate = totalBills > 0
                ? BigDecimal.valueOf((double) paidCount / totalBills * 100)
                      .setScale(1, RoundingMode.HALF_UP)
                      .doubleValue()
                : 0.0;

        List<Object[]> monthlyRaw = billRepository.getMonthlyRevenue(currentYear);
        Map<Integer, BigDecimal> monthlyMap = new HashMap<>();
        for (Object[] row : monthlyRaw) {
            int month = ((Number) row[0]).intValue();
            BigDecimal amount = new BigDecimal(row[1].toString());
            monthlyMap.put(month, amount);
        }

        List<Map<String, Object>> monthlyData = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("month", MONTH_NAMES[m]);
            entry.put("amount", monthlyMap.getOrDefault(m, BigDecimal.ZERO));
            monthlyData.add(entry);
        }

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("totalRevenueYTD",     ytdRevenue);
        dashboard.put("totalBillsGenerated", totalBills);
        dashboard.put("collectionRate",      collectionRate);
        dashboard.put("monthlyData",         monthlyData);
        return dashboard;
    }

    public Map<String, Object> getAnalyticsData() {
        List<Object[]> methodRaw = billRepository.getPaymentMethodBreakdown();
        List<Map<String, Object>> paymentMethods = new ArrayList<>();

        BigDecimal totalVolume = methodRaw.stream()
                .map(row -> new BigDecimal(row[2].toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Object[] row : methodRaw) {
            String method = row[0].toString();
            long count = ((Number) row[1]).longValue();
            BigDecimal volume = new BigDecimal(row[2].toString());

            double percent = totalVolume.compareTo(BigDecimal.ZERO) > 0
                    ? volume.divide(totalVolume, 4, RoundingMode.HALF_UP)
                           .multiply(BigDecimal.valueOf(100))
                           .doubleValue()
                    : 0.0;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name",    formatMethodName(method));
            entry.put("percent", Math.round(percent));
            entry.put("volume",  volume);
            paymentMethods.add(entry);
        }

        long paidCount    = billRepository.countByStatus(Bill.BillStatus.PAID);
        long pendingCount = billRepository.countByStatus(Bill.BillStatus.PENDING);
        long overdueCount = billRepository.countByStatus(Bill.BillStatus.OVERDUE);
        long total        = paidCount + pendingCount + overdueCount;

        List<Map<String, Object>> billStatusBreakdown = new ArrayList<>();
        billStatusBreakdown.add(buildStatusEntry("Paid & Cleared", paidCount, total));
        billStatusBreakdown.add(buildStatusEntry("Awaiting Settlement", pendingCount, total));
        billStatusBreakdown.add(buildStatusEntry("Overdue / Escalated", overdueCount, total));

        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("paymentMethods",      paymentMethods);
        analytics.put("billStatusBreakdown", billStatusBreakdown);
        return analytics;
    }

    private Map<String, Object> buildStatusEntry(String name, long count, long total) {
        double percent = total > 0 ? (double) count / total * 100 : 0;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name",    name);
        entry.put("count",   count);
        entry.put("percent", (int) Math.round(percent));
        return entry;
    }

    private String formatMethodName(String method) {
        return switch (method) {
            case "UPI"           -> "UPI (GPay / PhonePe / Paytm / BHIM)";
            case "NET_BANKING"   -> "Corporate Net Banking (SBI / HDFC / ICICI)";
            case "RUPAY_CARD"    -> "RuPay & Commercial Cards";
            case "NEFT_RTGS"     -> "Direct Bank Wire (NEFT / RTGS)";
            case "CREDIT_CARD"   -> "Credit Card";
            case "BANK_TRANSFER" -> "Bank Transfer";
            case "PAYPAL"        -> "PayPal";
            case "STRIPE"        -> "Stripe";
            default              -> method;
        };
    }
}
