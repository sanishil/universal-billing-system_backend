package com.billing.backend.service;

import com.billing.backend.entity.Bill;
import com.billing.backend.entity.Customer;
import com.billing.backend.entity.Payment;
import com.billing.backend.repository.BillRepository;
import com.billing.backend.repository.CustomerRepository;
import com.billing.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
        stats.put("gstCollected",  billRepository.getGstCollected());
        stats.put("nonGstSales",   billRepository.getNonGstSales());
        return stats;
    }

    /** Everything the Reports page shows, all computed from saved bills, customers and payments */
    public Map<String, Object> getDashboardData() {
        int currentYear = LocalDate.now().getYear();
        List<Bill> bills = billRepository.findAll();

        BigDecimal ytdRevenue = billRepository.getTotalRevenueByYear(currentYear);
        BigDecimal lastYearRevenue = billRepository.getTotalRevenueByYear(currentYear - 1);
        // null when there is nothing to compare against
        Long yoyGrowth = lastYearRevenue.compareTo(BigDecimal.ZERO) > 0
                ? ytdRevenue.subtract(lastYearRevenue)
                      .multiply(BigDecimal.valueOf(100))
                      .divide(lastYearRevenue, 0, RoundingMode.HALF_UP)
                      .longValue()
                : null;

        long totalBills = bills.size();
        long paidCount = bills.stream().filter(b -> b.getStatus() == Bill.BillStatus.PAID).count();
        double collectionRate = totalBills > 0
                ? BigDecimal.valueOf((double) paidCount / totalBills * 100)
                      .setScale(1, RoundingMode.HALF_UP)
                      .doubleValue()
                : 0.0;

        List<Bill> unpaid = bills.stream().filter(b -> b.getStatus() != Bill.BillStatus.PAID).toList();
        BigDecimal outstandingAmount = unpaid.stream().map(Bill::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate today = LocalDate.now();
        long overdueCount = unpaid.stream()
                .filter(b -> b.getStatus() == Bill.BillStatus.OVERDUE
                        || (b.getDueDate() != null && b.getDueDate().isBefore(today)))
                .count();

        BigDecimal invoicedTotal = bills.stream().map(Bill::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgInvoiceValue = totalBills > 0
                ? invoicedTotal.divide(BigDecimal.valueOf(totalBills), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Customer> customers = customerRepository.findAll().stream()
                .filter(c -> c.getStatus() == Customer.CustomerStatus.ACTIVE)
                .toList();
        LocalDate quarterStart = today.withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
        long newCustomersThisQuarter = customers.stream()
                .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().toLocalDate().isBefore(quarterStart))
                .count();

        BigDecimal taxableSales = bills.stream()
                .filter(b -> b.getStatus() == Bill.BillStatus.PAID && b.getGstRate() != null)
                .map(Bill::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("totalRevenueYTD",         ytdRevenue);
        dashboard.put("yoyGrowthPercent",        yoyGrowth);
        dashboard.put("totalBillsGenerated",     totalBills);
        dashboard.put("paidCount",               paidCount);
        dashboard.put("collectionRate",          collectionRate);
        dashboard.put("outstandingAmount",       outstandingAmount);
        dashboard.put("unpaidCount",             unpaid.size());
        dashboard.put("overdueCount",            overdueCount);
        dashboard.put("customerCount",           customers.size());
        dashboard.put("newCustomersThisQuarter", newCustomersThisQuarter);
        dashboard.put("avgInvoiceValue",         avgInvoiceValue);
        dashboard.put("avgDaysToPay",            averageDaysToPay(bills));
        dashboard.put("taxableSales",            taxableSales);
        dashboard.put("taxCollected",            billRepository.getGstCollected());
        dashboard.put("nonGstSales",             billRepository.getNonGstSales());
        dashboard.put("revenueByItem",           revenueByItem(bills));
        dashboard.put("monthlyData",             monthlyData(currentYear));
        return dashboard;
    }

    /** Settled revenue per month; value is the bar height as a percent of the best month */
    private List<Map<String, Object>> monthlyData(int year) {
        Map<Integer, BigDecimal> monthlyMap = new HashMap<>();
        for (Object[] row : billRepository.getMonthlyRevenue(year)) {
            monthlyMap.put(((Number) row[0]).intValue(), new BigDecimal(row[1].toString()));
        }
        BigDecimal best = monthlyMap.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        List<Map<String, Object>> monthlyData = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            BigDecimal amount = monthlyMap.getOrDefault(m, BigDecimal.ZERO);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("month",  MONTH_NAMES[m]);
            entry.put("amount", shortRupees(amount));
            entry.put("value",  percentOf(amount, best));
            monthlyData.add(entry);
        }
        return monthlyData;
    }

    /** Top 4 line items (Room Rent, Electricity Charges, ...) by settled revenue */
    private List<Map<String, Object>> revenueByItem(List<Bill> bills) {
        Map<String, BigDecimal> byName = new HashMap<>();
        bills.stream()
                .filter(b -> b.getStatus() == Bill.BillStatus.PAID && b.getItems() != null)
                .flatMap(b -> b.getItems().stream())
                .forEach(item -> byName.merge(item.getName().trim(), item.getAmount(), BigDecimal::add));
        BigDecimal total = byName.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return byName.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(4)
                .map(e -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name",    e.getKey());
                    entry.put("amount",  e.getValue());
                    entry.put("percent", percentOf(e.getValue(), total));
                    return entry;
                })
                .toList();
    }

    /** Days from issue to first successful payment, over paid bills; null with no payments yet */
    private Double averageDaysToPay(List<Bill> bills) {
        Map<String, LocalDate> issued = new HashMap<>();
        bills.forEach(b -> { if (b.getCreatedAt() != null) issued.put(b.getId(), b.getCreatedAt()); });

        Map<String, LocalDate> firstPaid = new HashMap<>();
        paymentRepository.findByStatus(Payment.PaymentStatus.SUCCESS).forEach(p -> {
            if (p.getCreatedAt() != null && issued.containsKey(p.getBillId())) {
                firstPaid.merge(p.getBillId(), p.getCreatedAt().toLocalDate(), (a, b) -> a.isBefore(b) ? a : b);
            }
        });
        if (firstPaid.isEmpty()) return null;

        double avg = firstPaid.entrySet().stream()
                .mapToLong(e -> Math.max(0, ChronoUnit.DAYS.between(issued.get(e.getKey()), e.getValue())))
                .average()
                .orElse(0);
        return BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private int percentOf(BigDecimal part, BigDecimal whole) {
        if (whole.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.HALF_UP).intValue();
    }

    /** ₹950, ₹4.5K, ₹1.2L, ₹3.4Cr */
    private String shortRupees(BigDecimal amount) {
        double v = amount.doubleValue();
        if (v >= 1_00_00_000) return "₹" + trim(v / 1_00_00_000) + "Cr";
        if (v >= 1_00_000)    return "₹" + trim(v / 1_00_000) + "L";
        if (v >= 1_000)       return "₹" + trim(v / 1_000) + "K";
        return "₹" + Math.round(v);
    }

    private String trim(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
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
