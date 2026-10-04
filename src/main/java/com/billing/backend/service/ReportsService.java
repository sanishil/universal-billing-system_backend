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

/**
 * ReportsService — generates analytics and dashboard data.
 *
 * Endpoints served:
 *   GET /api/reports/stats       → bill counts + revenue totals
 *   GET /api/reports/dashboard   → YTD data + monthly breakdown
 *   GET /api/reports/analytics   → payment methods + bill status breakdown
 */
@Service
@RequiredArgsConstructor
public class ReportsService {

    private final BillRepository billRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    // Month names for the monthly report
    private static final String[] MONTH_NAMES = {
        "", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    // ── BILL STATS ────────────────────────────────────────────────────────────

    /**
     * Returns simple bill status counts and total revenue.
     *
     * Response shape:
     * {
     *   "totalRevenue": 1124900,
     *   "paidCount": 3,
     *   "pendingCount": 2,
     *   "overdueCount": 1,
     *   "totalCount": 6
     * }
     */
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

    // ── DASHBOARD DATA ────────────────────────────────────────────────────────

    /**
     * Returns YTD revenue, bill count, collection rate, and monthly breakdown.
     *
     * Response shape:
     * {
     *   "totalRevenueYTD": 12450000,
     *   "totalBillsGenerated": 1245,
     *   "collectionRate": 96.4,
     *   "monthlyData": [
     *     { "month": "Jan", "amount": 1240000 },
     *     ...
     *   ]
     * }
     */
    public Map<String, Object> getDashboardData() {
        int currentYear = LocalDate.now().getYear();

        // Year-to-date revenue (all PAID bills in current year)
        BigDecimal ytdRevenue = billRepository.getTotalRevenueByYear(currentYear);

        // Total bills generated (all time)
        long totalBills = billRepository.count();

        // Collection rate = paidCount / totalCount × 100
        long paidCount  = billRepository.countByStatus(Bill.BillStatus.PAID);
        double collectionRate = totalBills > 0
                ? BigDecimal.valueOf((double) paidCount / totalBills * 100)
                      .setScale(1, RoundingMode.HALF_UP)
                      .doubleValue()
                : 0.0;

        // Monthly revenue breakdown for current year
        List<Object[]> monthlyRaw = billRepository.getMonthlyRevenue(currentYear);
        Map<Integer, BigDecimal> monthlyMap = new HashMap<>();
        for (Object[] row : monthlyRaw) {
            int month = ((Number) row[0]).intValue();
            BigDecimal amount = new BigDecimal(row[1].toString());
            monthlyMap.put(month, amount);
        }

        // Build the monthly data array for months 1–12
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

    // ── ANALYTICS DATA ────────────────────────────────────────────────────────

    /**
     * Returns payment method breakdown and bill status breakdown.
     *
     * Response shape:
     * {
     *   "paymentMethods": [...],
     *   "billStatusBreakdown": [...]
     * }
     */
    public Map<String, Object> getAnalyticsData() {
        // ── Payment Method Breakdown ──────────────────────────────────────────
        List<Object[]> methodRaw = billRepository.getPaymentMethodBreakdown();
        List<Map<String, Object>> paymentMethods = new ArrayList<>();

        // Calculate total volume across all methods for percentage
        BigDecimal totalVolume = methodRaw.stream()
                .map(row -> new BigDecimal(row[2].toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Object[] row : methodRaw) {
            String method = row[0].toString();
            long count = ((Number) row[1]).longValue();
            BigDecimal volume = new BigDecimal(row[2].toString());

            // Calculate percentage of total volume
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

        // ── Bill Status Breakdown ─────────────────────────────────────────────
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

    // ── HELPERS ───────────────────────────────────────────────────────────────

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
