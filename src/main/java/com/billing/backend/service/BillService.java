package com.billing.backend.service;

import com.billing.backend.dto.GstBreakdown;
import com.billing.backend.entity.Bill;
import com.billing.backend.entity.BillEmailLog;
import com.billing.backend.entity.BillItem;
import com.billing.backend.entity.Business;
import com.billing.backend.entity.Customer;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.BillEmailLogRepository;
import com.billing.backend.repository.BillRepository;
import com.billing.backend.util.IndianCurrencyUtil;
import com.billing.backend.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final CustomerService customerService;
    private final SettingsService settingsService;
    private final BusinessService businessService;
    private final GstCalculator gstCalculator;
    private final TokenGenerator tokenGenerator;
    private final IndianCurrencyUtil currencyUtil;
    private final JavaMailSender mailSender;
    private final BillEmailLogRepository emailLogRepository;

    // Old share links looked like "bill-<customer>-<6 digits>" and could be guessed
    private static final String LEGACY_LINK_PREFIX = "bill-";

    @Value("${app.supplier.gstin}")
    private String supplierGstin;

    @Value("${app.supplier.state-code}")
    private String supplierStateCode;

    @Value("${spring.mail.username}")
    private String mailFrom;

    // Frontend URL used in the "view bill" link of bill emails
    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public static final String ISSUER = "ISSUER";
    public static final String RECIPIENT = "RECIPIENT";

    /**
     * scope=sent (default): bills the user issued. scope=received: bills issued to a customer
     * record linked to the user's login. Then filtered by status / search / customerId.
     */
    public List<Bill> getBills(String userId, String scope, String status, String search, String customerId) {
        boolean received = "received".equalsIgnoreCase(scope);
        List<Bill> bills = received
                ? billRepository.findReceivedBy(userId)
                : billRepository.findByIssuerIdOrderByCreatedAtDesc(userId);

        boolean hasStatus = StringUtils.hasText(status) && !status.equalsIgnoreCase("ALL");
        Bill.BillStatus billStatus = hasStatus && !status.equalsIgnoreCase("UNPAID") ? parseBillStatus(status) : null;
        String term = StringUtils.hasText(search) ? search.trim().toLowerCase() : null;

        return bills.stream()
                .filter(b -> !StringUtils.hasText(customerId) || customerId.equals(b.getCustomerId()))
                .filter(b -> !hasStatus
                        || (billStatus != null ? b.getStatus() == billStatus : b.getStatus() != Bill.BillStatus.PAID))
                .filter(b -> term == null
                        || b.getId().toLowerCase().contains(term)
                        || b.getCustomerName().toLowerCase().contains(term)
                        || (b.getSupplierName() != null && b.getSupplierName().toLowerCase().contains(term)))
                .sorted(Comparator.comparing(Bill::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(b -> decorate(b, received ? RECIPIENT : ISSUER))
                .toList();
    }

    /** Throws 404 unless the user issued the bill or it was issued to them */
    public Bill getBillForUser(String id, String userId) {
        Bill bill = getBillById(id);
        return decorate(bill, roleOf(bill, userId));
    }

    public Bill getBillById(String id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", id));
    }

    /** Changes (edit, delete, email, share) are for the issuer only */
    private Bill getOwnBill(String id, String userId) {
        Bill bill = getBillById(id);
        if (!ISSUER.equals(roleOf(bill, userId))) {
            throw new ResourceNotFoundException("Bill", id);
        }
        return bill;
    }

    private String roleOf(Bill bill, String userId) {
        // Bills from before issuers were recorded belong to whoever can see them
        if (bill.getIssuerId() == null || bill.getIssuerId().equals(userId)) {
            return ISSUER;
        }
        boolean isRecipient = customerService.findByUserid(userId).stream()
                .anyMatch(c -> c.getId().equals(bill.getCustomerId()));
        if (isRecipient) {
            return RECIPIENT;
        }
        throw new ResourceNotFoundException("Bill", bill.getId());
    }

    /** Fills the response-only fields */
    public Bill decorate(Bill bill, String role) {
        bill.setMyRole(role);
        bill.setPublicUrl(publicUrl(bill));
        emailLogRepository.findFirstByBillIdOrderByCreatedAtDesc(bill.getId())
                .ifPresent(log -> bill.setLastEmailStatus(log.getStatus()));

        Map<String, Object> issuer = new LinkedHashMap<>();
        issuer.put("id", bill.getIssuerId());
        issuer.put("name", bill.getSupplierName());
        issuer.put("businessName", bill.getSupplierName());
        issuer.put("gstin", StringUtils.hasText(bill.getSupplierGstin()) ? bill.getSupplierGstin() : null);
        issuer.put("stateCode", StringUtils.hasText(bill.getSupplierStateCode()) ? bill.getSupplierStateCode() : supplierStateCode);
        issuer.put("address", bill.getSupplierAddress());
        bill.setIssuer(StringUtils.hasText(bill.getSupplierName()) ? issuer : null);
        return bill;
    }

    private String publicUrl(Bill bill) {
        return publicBaseUrl + "/bill/view/" + bill.getUniqueLink();
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
    public Bill createBill(String issuerId, Bill billData, List<BillItem> items, boolean gstRateProvided,
                           boolean sendEmail, boolean inviteRecipient) {
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
        Business business = sellerBusiness(issuerId, customer);
        if (sendEmail && !StringUtils.hasText(customer.getEmail())) {
            throw new BadRequestException("\"" + customer.getName() + "\" has no email address. Add one to the customer or untick \"Email bill to customer\".");
        }
        String supplierStateCode = business.getStateCode();

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

        // No notes means none on the invoice; no default text is added
        String notes = StringUtils.hasText(billData.getNotes()) ? billData.getNotes().trim() : null;

        String pan = firstNonBlank(billData.getPan(), customer.getPan());
        String customerGstin = firstNonBlank(billData.getCustomerGstin(), customer.getGstin());

        Bill bill = Bill.builder()
                .id(billId)
                .customerId(billData.getCustomerId())
                .customerName(billData.getCustomerName().trim())
                .customerGstin(customerGstin != null ? customerGstin.toUpperCase() : null)
                .issuerId(issuerId)
                .businessId(business.getId())
                .supplierName(business.getName())
                .supplierAddress(business.getAddress())
                .supplierStateCode(supplierStateCode)
                .supplierGstin(business.getGstin() != null ? business.getGstin() : "")
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
        // Issued as already settled: counts towards the customer's total like mark-paid does
        if (savedBill.getStatus() == Bill.BillStatus.PAID) {
            customerService.addToTotalSpent(billData.getCustomerId(), savedBill.getTotal());
        }
        if (inviteRecipient) {
            customerService.giveLogin(billData.getCustomerId());
        }
        if (sendEmail) {
            emailBill(savedBill, customer.getEmail(), null, null, business.getName(),
                    inviteRecipient ? "BILL_WITH_INVITE" : "BILL");
        }

        return decorate(savedBill, ISSUER);
    }

    /**
     * @param gstRateProvided true when the client sent the gstRate key (null then means No GST);
     *                        false → keep the bill's current rate
     */
    @Transactional
    public Bill updateBill(String id, String userId, Bill updatedData, List<BillItem> newItems, boolean gstRateProvided) {
        Bill existing = getOwnBill(id, userId);
        boolean wasPaid = existing.getStatus() == Bill.BillStatus.PAID;
        BigDecimal oldTotal = existing.getTotal();

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
                    existing.getItems(), gstRate,
                    StringUtils.hasText(existing.getSupplierStateCode()) ? existing.getSupplierStateCode() : supplierStateCode,
                    existing.getStateCode());
            applyTotals(existing, totals);
        }

        Bill saved = billRepository.save(existing);

        // Paid bills count towards the customer's total; move it when status or amount changes
        boolean isPaid = saved.getStatus() == Bill.BillStatus.PAID;
        if (wasPaid) customerService.addToTotalSpent(saved.getCustomerId(), oldTotal.negate());
        if (isPaid) customerService.addToTotalSpent(saved.getCustomerId(), saved.getTotal());

        return decorate(saved, ISSUER);
    }

    /** POST /bills/:id/send-email — to defaults to the customer's email */
    public BillEmailLog sendBillEmail(String id, String userId, String to, List<String> cc, String message) {
        Bill bill = getOwnBill(id, userId);
        String recipient = to;
        if (!StringUtils.hasText(recipient)) {
            Customer customer = customerService.getCustomerById(bill.getCustomerId());
            recipient = customer.getEmail();
            if (!StringUtils.hasText(recipient)) {
                throw new BadRequestException("\"" + customer.getName() + "\" has no email address. Enter one to send the bill to.");
            }
        }
        if (!recipient.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BadRequestException("Invalid email address: " + recipient);
        }
        String sellerName = StringUtils.hasText(bill.getSupplierName()) ? bill.getSupplierName() : "Universal Billing System";
        return emailBill(bill, recipient.trim(), cc, message, sellerName, "BILL");
    }

    public List<BillEmailLog> getEmailLogs(String id, String userId) {
        getOwnBill(id, userId);
        return emailLogRepository.findByBillIdOrderByCreatedAtDesc(id);
    }

    /** A failed email doesn't undo the bill; it is logged and the bill can be shared by link */
    private BillEmailLog emailBill(Bill bill, String to, List<String> cc, String extraMessage,
                                  String sellerName, String type) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        if (cc != null && !cc.isEmpty()) {
            message.setCc(cc.stream().filter(StringUtils::hasText).map(String::trim).toArray(String[]::new));
        }
        message.setSubject("Invoice " + bill.getId() + " from " + sellerName);
        message.setText(
                "Hello " + bill.getCustomerName() + ",\n\n" +
                (StringUtils.hasText(extraMessage) ? extraMessage.trim() + "\n\n" : "") +
                sellerName + " has issued you invoice " + bill.getId() + ".\n\n" +
                "Amount: Rs. " + bill.getTotal().toPlainString() + " (" + bill.getAmountInWords() + ")\n" +
                "Due date: " + bill.getDueDate() + "\n" +
                "Status: " + bill.getStatus() + "\n\n" +
                "View or download the invoice: " + publicUrl(bill) + "\n\n" +
                (StringUtils.hasText(bill.getNotes()) ? bill.getNotes() + "\n\n" : "") +
                "Regards,\n" + sellerName);

        BillEmailLog log = BillEmailLog.builder().billId(bill.getId()).toEmail(to).type(type).build();
        try {
            mailSender.send(message);
            log.setStatus("SENT");
            log.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            BillService.log.warn("Email of bill {} to {} failed: {}", bill.getId(), to, e.getMessage());
            log.setStatus("FAILED");
            log.setError(e.getMessage());
        }
        return emailLogRepository.save(log);
    }

    /**
     * The business the bill is sold from: the customer's business when it is the issuer's own,
     * otherwise the issuer's default (first) business.
     */
    private Business sellerBusiness(String issuerId, Customer customer) {
        List<Business> businesses = businessService.getBusinesses(issuerId);
        Business business = businesses.stream()
                .filter(b -> b.getId().equals(customer.getBusinessId()))
                .findFirst()
                .orElse(businesses.isEmpty() ? null : businesses.get(0));
        if (business == null) {
            throw new BadRequestException("Add your business in Profile Settings before issuing bills");
        }
        if (!StringUtils.hasText(business.getStateCode())) {
            throw new BadRequestException("Add the state of \"" + business.getName() + "\" in Profile Settings before issuing bills");
        }
        return business;
    }

    @Transactional
    public void deleteBill(String id, String userId) {
        Bill bill = getOwnBill(id, userId);
        String customerId = bill.getCustomerId();
        boolean wasPaid = bill.getStatus() == Bill.BillStatus.PAID;
        BigDecimal paidAmount = bill.getTotal();

        emailLogRepository.deleteByBillId(bill.getId());
        billRepository.delete(bill);

        customerService.decrementBillCount(customerId);
        if (wasPaid) {
            customerService.addToTotalSpent(customerId, paidAmount.negate());
        }
    }

    @Transactional
    public Bill markAsPaid(String id, String userId) {
        Bill bill = getBillById(id);
        String role = roleOf(bill, userId);

        if (bill.getStatus() == Bill.BillStatus.PAID) {
            throw new BadRequestException("Bill is already marked as PAID");
        }

        bill.setStatus(Bill.BillStatus.PAID);
        Bill savedBill = billRepository.save(bill);

        customerService.addToTotalSpent(bill.getCustomerId(), bill.getTotal());

        return decorate(savedBill, role);
    }

    /** Counts and settled revenue of the same bills the list shows for this scope */
    public Map<String, Object> getBillStats(String userId, String scope) {
        List<Bill> bills = getBills(userId, scope, null, null, null);
        BigDecimal totalRevenue = bills.stream()
                .filter(b -> b.getStatus() == Bill.BillStatus.PAID)
                .map(Bill::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRevenue", totalRevenue);
        stats.put("paidCount", bills.stream().filter(b -> b.getStatus() == Bill.BillStatus.PAID).count());
        stats.put("pendingCount", bills.stream().filter(b -> b.getStatus() == Bill.BillStatus.PENDING).count());
        stats.put("overdueCount", bills.stream().filter(b -> b.getStatus() == Bill.BillStatus.OVERDUE).count());
        stats.put("totalCount", bills.size());
        return stats;
    }

    /**
     * The bill's public link. regenerate=true makes a new random token so old copies stop working;
     * bills with an old guessable link always get a new one.
     */
    @Transactional
    public String shareLink(String billId, String userId, boolean regenerate) {
        Bill bill = getOwnBill(billId, userId);
        if (regenerate || bill.getUniqueLink() == null || bill.getUniqueLink().startsWith(LEGACY_LINK_PREFIX)) {
            bill.setUniqueLink(newUniqueLink());
            billRepository.save(bill);
        }
        return publicUrl(bill);
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
