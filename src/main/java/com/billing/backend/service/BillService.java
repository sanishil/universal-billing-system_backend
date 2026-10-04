package com.billing.backend.service;

import com.billing.backend.entity.Bill;
import com.billing.backend.entity.BillItem;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.BillRepository;
import com.billing.backend.util.IndianCurrencyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * BillService — all business logic for the Bill/Invoice module.
 *
 * This is the most complex service. Key responsibilities:
 *  - Validate all bill fields
 *  - Server-side GST computation (NEVER trust client values)
 *  - Auto-generate bill ID and unique share link
 *  - Convert total to Indian words
 *  - Coordinate with CustomerService when bill status changes
 *
 * @Transactional ensures the bill + items are saved atomically.
 * If anything fails, all DB changes are rolled back.
 */
@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final CustomerService customerService;
    private final IndianCurrencyUtil currencyUtil;

    // Allowed GST rates per Indian GST law (Rule 3)
    private static final Set<Integer> VALID_GST_RATES = Set.of(0, 5, 12, 18, 28);

    // Supplier GSTIN — always this constant (Rule 1)
    @Value("${app.supplier.gstin}")
    private String supplierGstin;

    @Value("${app.supplier.state-code}")
    private String supplierStateCode;

    // ── GET ALL BILLS ─────────────────────────────────────────────────────────

    /**
     * Return all bills with optional filtering.
     * Supports: status filter, search (by bill ID or customer name), customerId filter.
     */
    public List<Bill> getAllBills(String status, String search, String customerId) {

        // Filter by customer ID (for customer detail page)
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

    // ── GET BILL BY ID ────────────────────────────────────────────────────────

    public Bill getBillById(String id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", id));
    }

    // ── GET BILL BY UNIQUE LINK (Public) ──────────────────────────────────────

    public Bill getBillByUniqueLink(String uniqueLink) {
        return billRepository.findByUniqueLink(uniqueLink)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bill with link '" + uniqueLink + "' not found"));
    }

    // ── CREATE BILL ───────────────────────────────────────────────────────────

    /**
     * Create a new bill with full server-side GST computation.
     *
     * Step-by-step per the specification:
     *  1.  Validate all fields
     *  2.  Validate each item (name, qty > 0, price >= 0)
     *  3.  Validate GST rate is in {0, 5, 12, 18, 28}
     *  4.  Compute subtotal  = Σ(qty × price)
     *  5.  Compute tax       = subtotal × gstRate%
     *  6.  Compute CGST/SGST or IGST based on isInterState
     *  7.  Compute total     = subtotal + tax
     *  8.  Convert total to Indian words
     *  9.  Generate bill ID: INV-YYYY-XXXXXX
     *  10. Generate unique share link slug
     *  11. Set all system constants (supplierGstin, currency)
     *  12. Assign defaults (hsnSac, dueDate, notes)
     *  13. Verify customerId exists
     *  14. Insert bill into DB
     *  15. Update customer.billsCount + 1
     */
    @Transactional
    public Bill createBill(Bill billData, List<BillItem> items) {

        // ── Step 1: Validate required fields ──────────────────────────────────
        if (!StringUtils.hasText(billData.getCustomerId())) {
            throw new BadRequestException("customerId is required");
        }
        if (!StringUtils.hasText(billData.getCustomerName())) {
            throw new BadRequestException("customerName is required");
        }
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Bill must have at least one item (Rule 20)");
        }

        // ── Step 2: Validate each item ────────────────────────────────────────
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

        // ── Step 3: Validate GST Rate ─────────────────────────────────────────
        int gstRateInt = billData.getGstRate() != null
                ? billData.getGstRate().intValue() : 18;
        if (!VALID_GST_RATES.contains(gstRateInt)) {
            throw new BadRequestException(
                    "Invalid GST rate. Allowed values: 0, 5, 12, 18, 28");
        }
        BigDecimal gstRate = new BigDecimal(gstRateInt);

        // ── Step 4: Compute SUBTOTAL ──────────────────────────────────────────
        // NEVER trust client-submitted totals (Rule 2)
        BigDecimal subtotal = BigDecimal.ZERO;
        for (BillItem item : items) {
            BigDecimal lineAmount = item.getQuantity().multiply(item.getPrice());
            subtotal = subtotal.add(lineAmount);
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        // ── Step 5: Compute TAX ───────────────────────────────────────────────
        // tax = subtotal × (gstRate / 100)
        BigDecimal tax = subtotal
                .multiply(gstRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // ── Step 6: Determine inter-state supply and split tax ────────────────
        // isInterState = (customer.stateCode != supplierStateCode)
        // Default: intra-state (Karnataka to Karnataka)
        boolean isInterState = billData.isInterState();

        BigDecimal cgst, sgst, igst;
        if (isInterState) {
            // Inter-state: IGST = full tax, CGST/SGST = 0
            cgst = BigDecimal.ZERO;
            sgst = BigDecimal.ZERO;
            igst = tax;
        } else {
            // Intra-state: CGST = SGST = tax/2, IGST = 0
            BigDecimal halfTax = tax.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            cgst = halfTax;
            sgst = halfTax;
            igst = BigDecimal.ZERO;
        }

        // ── Step 7: Compute TOTAL ─────────────────────────────────────────────
        BigDecimal total = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);

        // ── Step 8: Convert to Indian words ───────────────────────────────────
        String amountInWords = currencyUtil.numberToWords(total.doubleValue());

        // ── Step 9: Generate Bill ID ──────────────────────────────────────────
        // Format: INV-2026-XXXXXX (last 6 digits of current timestamp)
        String year = String.valueOf(LocalDate.now().getYear());
        String suffix = String.valueOf(System.currentTimeMillis()).substring(
                String.valueOf(System.currentTimeMillis()).length() - 6);
        String billId = "INV-" + year + "-" + suffix;

        // ── Step 10: Generate Unique Share Link ───────────────────────────────
        // slug = customerName (lowercase, alphanumeric only)
        String slug = billData.getCustomerName()
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        String uniqueLink = "bill-" + slug + "-" + suffix;

        // Ensure link is unique (very rare collision, but be safe)
        if (billRepository.existsByUniqueLink(uniqueLink)) {
            uniqueLink = uniqueLink + "-" + (System.currentTimeMillis() % 1000);
        }

        // ── Step 11: Set System Constants ────────────────────────────────────
        // Supplier GSTIN is ALWAYS the system constant (Rule 1)

        // ── Step 12: Assign Defaults ──────────────────────────────────────────
        LocalDate dueDate = billData.getDueDate() != null
                ? billData.getDueDate()
                : LocalDate.now().plusDays(14);  // Default: 14 days from today (Rule 6)

        String notes = StringUtils.hasText(billData.getNotes())
                ? billData.getNotes()
                : "Thank you for choosing Universal Billing System. " +
                  "Payment via UPI or Net Banking appreciated.";

        String pan = StringUtils.hasText(billData.getPan())
                ? billData.getPan() : "AAAAA0000A";

        String customerGstin = StringUtils.hasText(billData.getCustomerGstin())
                ? billData.getCustomerGstin() : "29AAAAA0000A1Z5";

        String placeOfSupply = StringUtils.hasText(billData.getPlaceOfSupply())
                ? billData.getPlaceOfSupply() : "Karnataka (29)";

        String stateCode = StringUtils.hasText(billData.getStateCode())
                ? billData.getStateCode() : "29";

        // ── Step 13: Verify customer exists ──────────────────────────────────
        customerService.getCustomerById(billData.getCustomerId());

        // ── Step 14: Build and save the Bill entity ───────────────────────────
        Bill bill = Bill.builder()
                .id(billId)
                .customerId(billData.getCustomerId())
                .customerName(billData.getCustomerName().trim())
                .customerGstin(customerGstin.toUpperCase())
                .supplierGstin(supplierGstin)
                .pan(pan.toUpperCase())
                .placeOfSupply(placeOfSupply)
                .stateCode(stateCode)
                .isInterState(isInterState)
                .gstRate(gstRate)
                .subtotal(subtotal)
                .cgst(cgst)
                .sgst(sgst)
                .igst(igst)
                .tax(tax)
                .total(total)
                .amountInWords(amountInWords)
                .currency("INR")
                .status(billData.getStatus() != null ? billData.getStatus() : Bill.BillStatus.PENDING)
                .uniqueLink(uniqueLink)
                .dueDate(dueDate)
                .notes(notes)
                .build();

        // Prepare items with computed amounts
        List<BillItem> billItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            BillItem item = items.get(i);
            String itemId = "item-" + System.currentTimeMillis() + "-" + i;
            BigDecimal amount = item.getQuantity()
                    .multiply(item.getPrice())
                    .setScale(2, RoundingMode.HALF_UP);

            BillItem savedItem = BillItem.builder()
                    .id(itemId)
                    .bill(bill)
                    .name(item.getName().trim())
                    .hsnSac(StringUtils.hasText(item.getHsnSac()) ? item.getHsnSac() : "998314")
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .amount(amount)
                    .build();
            billItems.add(savedItem);
        }
        bill.setItems(billItems);

        Bill savedBill = billRepository.save(bill);

        // ── Step 15: Update customer bill count ───────────────────────────────
        customerService.incrementBillCount(billData.getCustomerId());

        return savedBill;
    }

    // ── UPDATE BILL ───────────────────────────────────────────────────────────

    /**
     * Update an existing bill.
     * Re-computes all financial fields from scratch (Rule 2).
     */
    @Transactional
    public Bill updateBill(String id, Bill updatedData, List<BillItem> newItems) {
        Bill existing = getBillById(id);

        // Update basic fields if provided
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

        // If new items were provided, recompute all financials
        if (newItems != null && !newItems.isEmpty()) {
            // Validate items
            for (int i = 0; i < newItems.size(); i++) {
                BillItem item = newItems.get(i);
                if (!StringUtils.hasText(item.getName())) {
                    throw new BadRequestException("items[" + i + "].name is required");
                }
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("items[" + i + "].quantity must be > 0");
                }
                if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException("items[" + i + "].price must be >= 0");
                }
            }

            // Re-determine GST rate
            int gstRateInt = updatedData.getGstRate() != null
                    ? updatedData.getGstRate().intValue()
                    : existing.getGstRate().intValue();
            BigDecimal gstRate = new BigDecimal(gstRateInt);

            // Re-compute subtotal
            BigDecimal subtotal = BigDecimal.ZERO;
            for (BillItem item : newItems) {
                subtotal = subtotal.add(item.getQuantity().multiply(item.getPrice()));
            }
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

            // Re-compute tax and components
            BigDecimal tax = subtotal.multiply(gstRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            boolean isInterState = existing.isInterState();
            BigDecimal cgst, sgst, igst;
            if (isInterState) {
                cgst = BigDecimal.ZERO; sgst = BigDecimal.ZERO; igst = tax;
            } else {
                BigDecimal half = tax.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
                cgst = half; sgst = half; igst = BigDecimal.ZERO;
            }

            BigDecimal total = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);

            existing.setGstRate(gstRate);
            existing.setSubtotal(subtotal);
            existing.setCgst(cgst);
            existing.setSgst(sgst);
            existing.setIgst(igst);
            existing.setTax(tax);
            existing.setTotal(total);
            existing.setAmountInWords(currencyUtil.numberToWords(total.doubleValue()));

            // Replace items
            existing.getItems().clear();
            for (int i = 0; i < newItems.size(); i++) {
                BillItem item = newItems.get(i);
                BigDecimal amount = item.getQuantity().multiply(item.getPrice())
                        .setScale(2, RoundingMode.HALF_UP);
                BillItem bi = BillItem.builder()
                        .id("item-" + System.currentTimeMillis() + "-" + i)
                        .bill(existing)
                        .name(item.getName().trim())
                        .hsnSac(StringUtils.hasText(item.getHsnSac()) ? item.getHsnSac() : "998314")
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .amount(amount)
                        .build();
                existing.getItems().add(bi);
            }
        }

        return billRepository.save(existing);
    }

    // ── DELETE BILL ───────────────────────────────────────────────────────────

    /**
     * Hard delete a bill and update customer counters.
     */
    @Transactional
    public void deleteBill(String id) {
        Bill bill = getBillById(id);
        String customerId = bill.getCustomerId();
        boolean wasPaid = bill.getStatus() == Bill.BillStatus.PAID;
        BigDecimal paidAmount = bill.getTotal();

        billRepository.delete(bill);

        // Update customer counters
        customerService.decrementBillCount(customerId);
        if (wasPaid) {
            // Reverse the totalSpent (subtract what was paid)
            customerService.addToTotalSpent(customerId, paidAmount.negate());
        }
    }

    // ── MARK BILL AS PAID ─────────────────────────────────────────────────────

    /**
     * Mark a bill as PAID and update customer's total spend.
     * Called by: admin marking manually, or PaymentService after payment.
     */
    @Transactional
    public Bill markAsPaid(String id) {
        Bill bill = getBillById(id);

        // Only mark as paid if currently PENDING or OVERDUE
        if (bill.getStatus() == Bill.BillStatus.PAID) {
            throw new BadRequestException("Bill is already marked as PAID");
        }

        bill.setStatus(Bill.BillStatus.PAID);
        Bill savedBill = billRepository.save(bill);

        // Update customer's totalSpent
        customerService.addToTotalSpent(bill.getCustomerId(), bill.getTotal());

        return savedBill;
    }

    // ── GET STATS ─────────────────────────────────────────────────────────────

    /**
     * Return revenue and count statistics for the reports module.
     */
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

    // ── GENERATE SHARE LINK ───────────────────────────────────────────────────

    /**
     * Return the shareable URL for a bill.
     */
    public String generateShareLink(String billId, String baseUrl) {
        Bill bill = getBillById(billId);
        return baseUrl + "/bill/view/" + bill.getUniqueLink();
    }

    // ── MARK OVERDUE (can be called by scheduled job) ─────────────────────────

    /**
     * Auto-flag all past-due PENDING bills as OVERDUE.
     */
    @Transactional
    public void markOverdueBills() {
        List<Bill> overdueBills = billRepository.findOverdueBills(LocalDate.now());
        overdueBills.forEach(bill -> bill.setStatus(Bill.BillStatus.OVERDUE));
        billRepository.saveAll(overdueBills);
    }

    // ── HELPER ───────────────────────────────────────────────────────────────

    private Bill.BillStatus parseBillStatus(String status) {
        try {
            return Bill.BillStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status. Use: PAID, PENDING, or OVERDUE");
        }
    }
}
