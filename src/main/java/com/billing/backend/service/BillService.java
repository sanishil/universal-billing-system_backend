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

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final CustomerService customerService;
    private final IndianCurrencyUtil currencyUtil;

    private static final Set<Integer> VALID_GST_RATES = Set.of(0, 5, 12, 18, 28);

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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bill with link '" + uniqueLink + "' not found"));
    }

    @Transactional
    public Bill createBill(Bill billData, List<BillItem> items) {
        if (!StringUtils.hasText(billData.getCustomerId())) {
            throw new BadRequestException("customerId is required");
        }
        if (!StringUtils.hasText(billData.getCustomerName())) {
            throw new BadRequestException("customerName is required");
        }
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

        int gstRateInt = billData.getGstRate() != null
                ? billData.getGstRate().intValue() : 18;
        if (!VALID_GST_RATES.contains(gstRateInt)) {
            throw new BadRequestException("Invalid GST rate. Allowed values: 0, 5, 12, 18, 28");
        }
        BigDecimal gstRate = new BigDecimal(gstRateInt);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (BillItem item : items) {
            BigDecimal lineAmount = item.getQuantity().multiply(item.getPrice());
            subtotal = subtotal.add(lineAmount);
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tax = subtotal
                .multiply(gstRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        boolean isInterState = billData.isInterState();

        BigDecimal cgst, sgst, igst;
        if (isInterState) {
            cgst = BigDecimal.ZERO;
            sgst = BigDecimal.ZERO;
            igst = tax;
        } else {
            BigDecimal halfTax = tax.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            cgst = halfTax;
            sgst = halfTax;
            igst = BigDecimal.ZERO;
        }

        BigDecimal total = subtotal.add(tax).setScale(2, RoundingMode.HALF_UP);

        String amountInWords = currencyUtil.numberToWords(total.doubleValue());

        String year = String.valueOf(LocalDate.now().getYear());
        String suffix = String.valueOf(System.currentTimeMillis()).substring(
                String.valueOf(System.currentTimeMillis()).length() - 6);
        String billId = "INV-" + year + "-" + suffix;

        String slug = billData.getCustomerName()
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        String uniqueLink = "bill-" + slug + "-" + suffix;

        if (billRepository.existsByUniqueLink(uniqueLink)) {
            uniqueLink = uniqueLink + "-" + (System.currentTimeMillis() % 1000);
        }

        LocalDate dueDate = billData.getDueDate() != null
                ? billData.getDueDate()
                : LocalDate.now().plusDays(14);

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

        customerService.getCustomerById(billData.getCustomerId());

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

        customerService.incrementBillCount(billData.getCustomerId());

        return savedBill;
    }

    @Transactional
    public Bill updateBill(String id, Bill updatedData, List<BillItem> newItems) {
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

        if (newItems != null && !newItems.isEmpty()) {
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

            int gstRateInt = updatedData.getGstRate() != null
                    ? updatedData.getGstRate().intValue()
                    : existing.getGstRate().intValue();
            BigDecimal gstRate = new BigDecimal(gstRateInt);

            BigDecimal subtotal = BigDecimal.ZERO;
            for (BillItem item : newItems) {
                subtotal = subtotal.add(item.getQuantity().multiply(item.getPrice()));
            }
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

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

    public String generateShareLink(String billId, String baseUrl) {
        Bill bill = getBillById(billId);
        return baseUrl + "/bill/view/" + bill.getUniqueLink();
    }

    @Transactional
    public void markOverdueBills() {
        List<Bill> overdueBills = billRepository.findOverdueBills(LocalDate.now());
        overdueBills.forEach(bill -> bill.setStatus(Bill.BillStatus.OVERDUE));
        billRepository.saveAll(overdueBills);
    }

    private Bill.BillStatus parseBillStatus(String status) {
        try {
            return Bill.BillStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status. Use: PAID, PENDING, or OVERDUE");
        }
    }
}
