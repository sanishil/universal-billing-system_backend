package com.billing.backend.service;

import com.billing.backend.dto.GstBreakdown;
import com.billing.backend.entity.Bill;
import com.billing.backend.entity.BillItem;
import com.billing.backend.entity.Customer;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.BillRepository;
import com.billing.backend.util.IndianCurrencyUtil;
import com.billing.backend.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final CustomerService customerService;
    private final SettingsService settingsService;
    private final GstCalculator gstCalculator;
    private final TokenGenerator tokenGenerator;
    private final IndianCurrencyUtil currencyUtil;

    // Old share links looked like "bill-<customer>-<6 digits>" and could be guessed
    private static final String LEGACY_LINK_PREFIX = "bill-";

    @Value("${app.supplier.gstin}")
    private String supplierGstin;

    @Value("${app.supplier.state-code}")
    private String supplierStateCode;

    public List<Bill> getAllBills(String status, String search, String customerId) {
        if (StringUtils.hasText(customerId)) {
            return billRepository.findByCustomerId(customerId);
        }

        boolean hasSearch = StringUtils.hasText(search);
        boolean hasStatus = StringUtils.hasText(status) && !status.equalsIgnoreCase("ALL");

        if (hasSearch && hasStatus) {
            Bill.BillStatus billStatus = parseBillStatus(status);
            return billRepository.searchByIdOrCustomerNameAndStatus(search, billStatus);
        } else if (hasSearch) {
            return billRepository.searchByIdOrCustomerName(search);
        } else if (hasStatus) {
            return billRepository.findByStatus(parseBillStatus(status));
        } else {
            return billRepository.findAll();
        }
    }

    public Bill getBillById(String id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", id));
    }

    public Bill getBillByUniqueLink(String uniqueLink) {
        return billRepository.findByUniqueLink(uniqueLink)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found"));
    }

    /**
     * @param gstRateProvided true when the client sent the gstRate key (null then means No GST);
     *                        false → use the system default rate
     */
    @Transactional
    public Bill createBill(Bill billData, List<BillItem> items, boolean gstRateProvided) {
        if (!StringUtils.hasText(billData.getCustomerId())) {
            throw new BadRequestException("customerId is required");
        }
        if (!StringUtils.hasText(billData.getCustomerName())) {
            throw new BadRequestException("customerName is required");
        }
        validateItems(items);

        BigDecimal gstRate = validateGstRate(
                gstRateProvided ? billData.getGstRate() : settingsService.getDefaultGstRate());

        Customer customer = customerService.getCustomerById(billData.getCustomerId());

        String stateCode = StringUtils.hasText(billData.getStateCode())
                ? billData.getStateCode().trim()
                : (StringUtils.hasText(customer.getStateCode()) ? customer.getStateCode() : supplierStateCode);

        String placeOfSupply = StringUtils.hasText(billData.getPlaceOfSupply())
                ? billData.getPlaceOfSupply()
                : (StringUtils.hasText(customer.getState())
                        ? customer.getState() + " (" + stateCode + ")" : "Karnataka (29)");

        GstBreakdown totals = gstCalculator.calculate(items, gstRate, supplierStateCode, stateCode);

        String year = String.valueOf(LocalDate.now().getYear());
        String suffix = String.valueOf(System.currentTimeMillis()).substring(
                String.valueOf(System.currentTimeMillis()).length() - 6);
        String billId = "INV-" + year + "-" + suffix;

        LocalDate dueDate = billData.getDueDate() != null
                ? billData.getDueDate()
                : LocalDate.now().plusDays(14);

        String notes = StringUtils.hasText(billData.getNotes())
                ? billData.getNotes()
                : "Thank you for choosing Universal Billing System. " +
                  "Payment via UPI or Net Banking appreciated.";

        String pan = firstNonBlank(billData.getPan(), customer.getPan());
        String customerGstin = firstNonBlank(billData.getCustomerGstin(), customer.getGstin());

        Bill bill = Bill.builder()
                .id(billId)
                .customerId(billData.getCustomerId())
                .customerName(billData.getCustomerName().trim())
                .customerGstin(customerGstin != null ? customerGstin.toUpperCase() : null)
                .supplierGstin(supplierGstin)
                .pan(pan != null ? pan.toUpperCase() : null)
                .placeOfSupply(placeOfSupply)
                .stateCode(stateCode)
                .currency("INR")
                .status(billData.getStatus() != null ? billData.getStatus() : Bill.BillStatus.PENDING)
                .uniqueLink(newUniqueLink())
                .dueDate(dueDate)
                .notes(notes)
                .build();

        applyTotals(bill, totals);
        bill.setItems(buildItems(bill, items));

        Bill savedBill = billRepository.save(bill);

        customerService.incrementBillCount(billData.getCustomerId());

        return savedBill;
    }

    /**
     * @param gstRateProvided true when the client sent the gstRate key (null then means No GST);
     *                        false → keep the bill's current rate
     */
    @Transactional
    public Bill updateBill(String id, Bill updatedData, List<BillItem> newItems, boolean gstRateProvided) {
        Bill existing = getBillById(id);

        if (StringUtils.hasText(updatedData.getCustomerName())) {
            existing.setCustomerName(updatedData.getCustomerName().trim());
        }
        if (StringUtils.hasText(updatedData.getNotes())) {
            existing.setNotes(updatedData.getNotes());
        }
        if (updatedData.getDueDate() != null) {
            existing.setDueDate(updatedData.getDueDate());
        }
        if (updatedData.getStatus() != null) {
            existing.setStatus(updatedData.getStatus());
        }

        boolean hasNewItems = newItems != null && !newItems.isEmpty();
        if (hasNewItems || gstRateProvided) {
            BigDecimal gstRate = gstRateProvided
                    ? validateGstRate(updatedData.getGstRate())
                    : existing.getGstRate();

            if (hasNewItems) {
                validateItems(newItems);
                existing.getItems().clear();
                existing.getItems().addAll(buildItems(existing, newItems));
            }

            GstBreakdown totals = gstCalculator.calculate(
                    existing.getItems(), gstRate, supplierStateCode, existing.getStateCode());
            applyTotals(existing, totals);
        }

        return billRepository.save(existing);
    }

    @Transactional
    public void deleteBill(String id) {
        Bill bill = getBillById(id);
        String customerId = bill.getCustomerId();
        boolean wasPaid = bill.getStatus() == Bill.BillStatus.PAID;
        BigDecimal paidAmount = bill.getTotal();

        billRepository.delete(bill);

        customerService.decrementBillCount(customerId);
        if (wasPaid) {
            customerService.addToTotalSpent(customerId, paidAmount.negate());
        }
    }

    @Transactional
    public Bill markAsPaid(String id) {
        Bill bill = getBillById(id);

        if (bill.getStatus() == Bill.BillStatus.PAID) {
            throw new BadRequestException("Bill is already marked as PAID");
        }

        bill.setStatus(Bill.BillStatus.PAID);
        Bill savedBill = billRepository.save(bill);

        customerService.addToTotalSpent(bill.getCustomerId(), bill.getTotal());

        return savedBill;
    }

    public java.util.Map<String, Object> getBillStats() {
        BigDecimal totalRevenue = billRepository.getTotalRevenue();
        long paidCount = billRepository.countByStatus(Bill.BillStatus.PAID);
        long pendingCount = billRepository.countByStatus(Bill.BillStatus.PENDING);
        long overdueCount = billRepository.countByStatus(Bill.BillStatus.OVERDUE);
        long totalCount = billRepository.count();

        return java.util.Map.of(
                "totalRevenue", totalRevenue,
                "paidCount", paidCount,
                "pendingCount", pendingCount,
                "overdueCount", overdueCount,
                "totalCount", totalCount
        );
    }

    /**
     * Returns the absolute public URL. Bills that still have an old guessable link
     * get a new random token, so the old link stops working.
     */
    @Transactional
    public String generateShareLink(String billId, String publicBaseUrl) {
        Bill bill = getBillById(billId);
        if (bill.getUniqueLink() == null || bill.getUniqueLink().startsWith(LEGACY_LINK_PREFIX)) {
            bill.setUniqueLink(newUniqueLink());
            billRepository.save(bill);
        }
        return publicBaseUrl + "/bill/view/" + bill.getUniqueLink();
    }

    @Transactional
    public void markOverdueBills() {
        List<Bill> overdueBills = billRepository.findOverdueBills(LocalDate.now());
        overdueBills.forEach(bill -> bill.setStatus(Bill.BillStatus.OVERDUE));
        billRepository.saveAll(overdueBills);
    }

    private void validateItems(List<BillItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Bill must have at least one item");
        }
        for (int i = 0; i < items.size(); i++) {
            BillItem item = items.get(i);
            if (!StringUtils.hasText(item.getName())) {
                throw new BadRequestException("items[" + i + "].name is required");
            }
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("items[" + i + "].quantity must be greater than 0");
            }
            if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("items[" + i + "].price must be >= 0");
            }
        }
    }

    /** null (No GST) is always allowed; any other rate must be in the settings list. */
    private BigDecimal validateGstRate(BigDecimal gstRate) {
        if (gstRate == null) {
            return null;
        }
        List<BigDecimal> allowed = settingsService.getAllowedGstRates();
        boolean ok = allowed.stream().anyMatch(r -> r.compareTo(gstRate) == 0);
        if (!ok) {
            String list = allowed.stream().map(BigDecimal::toPlainString).collect(Collectors.joining(", "));
            throw new BadRequestException("Invalid GST rate. Allowed values: " + list + ", or null for No GST");
        }
        return gstRate;
    }

    private void applyTotals(Bill bill, GstBreakdown totals) {
        bill.setGstRate(totals.gstRate());
        bill.setInterState(totals.interState());
        bill.setSubtotal(totals.subtotal());
        bill.setCgst(totals.cgst());
        bill.setSgst(totals.sgst());
        bill.setIgst(totals.igst());
        bill.setTax(totals.tax());
        bill.setTotal(totals.total());
        bill.setAmountInWords(currencyUtil.numberToWords(totals.total().doubleValue()));
    }

    private List<BillItem> buildItems(Bill bill, List<BillItem> items) {
        List<BillItem> billItems = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < items.size(); i++) {
            BillItem item = items.get(i);
            billItems.add(BillItem.builder()
                    .id("item-" + now + "-" + i)
                    .bill(bill)
                    .name(item.getName().trim())
                    .hsnSac(StringUtils.hasText(item.getHsnSac()) ? item.getHsnSac() : "998314")
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .amount(gstCalculator.lineAmount(item))
                    .build());
        }
        return billItems;
    }

    private String newUniqueLink() {
        String link;
        do {
            link = tokenGenerator.urlSafe(32);
        } while (billRepository.existsByUniqueLink(link));
        return link;
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a.trim();
        if (StringUtils.hasText(b)) return b.trim();
        return null;
    }

    private Bill.BillStatus parseBillStatus(String status) {
        try {
            return Bill.BillStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status. Use: PAID, PENDING, or OVERDUE");
        }
    }
}
