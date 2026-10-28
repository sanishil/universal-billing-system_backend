package com.billing.backend.dto;

import com.billing.backend.entity.Bill;
import com.billing.backend.entity.BillItem;
import com.billing.backend.entity.SystemSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Safe subset of a bill for the public share link (no login).
 * Field names match the Bill entity JSON so the public page keeps working.
 * Deliberately excludes: customerId, notes, uniqueLink, updatedAt, payment history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicBillResponse {

    private String id;                 // invoice number, e.g. INV-2026-123456
    private String customerName;
    private String customerGstin;
    private String pan;
    private String supplierGstin;
    private String placeOfSupply;
    private String stateCode;
    private boolean isInterState;      // serialised as "interState", same as Bill

    private boolean gstApplicable;     // false → show "GST not applicable" / Bill of Supply
    private BigDecimal gstRate;
    private BigDecimal subtotal;
    private BigDecimal cgst;
    private BigDecimal sgst;
    private BigDecimal igst;
    private BigDecimal tax;
    private BigDecimal total;
    private String amountInWords;
    private String currency;

    private Bill.BillStatus status;
    private LocalDate createdAt;
    private LocalDate dueDate;

    private List<Item> items;
    private Issuer issuer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String name;
        private String hsnSac;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Issuer {
        private String name;
        private String gstin;
        private String address;
        private String email;
        private String phone;
    }

    public static PublicBillResponse from(Bill bill, SystemSettings settings) {
        return PublicBillResponse.builder()
                .id(bill.getId())
                .customerName(bill.getCustomerName())
                .customerGstin(bill.getCustomerGstin())
                .pan(bill.getPan())
                .supplierGstin(bill.getSupplierGstin())
                .placeOfSupply(bill.getPlaceOfSupply())
                .stateCode(bill.getStateCode())
                .isInterState(bill.isInterState())
                .gstApplicable(bill.isGstApplicable())
                .gstRate(bill.getGstRate())
                .subtotal(bill.getSubtotal())
                .cgst(bill.getCgst())
                .sgst(bill.getSgst())
                .igst(bill.getIgst())
                .tax(bill.getTax())
                .total(bill.getTotal())
                .amountInWords(bill.getAmountInWords())
                .currency(bill.getCurrency())
                .status(bill.getStatus())
                .createdAt(bill.getCreatedAt())
                .dueDate(bill.getDueDate())
                .items(bill.getItems().stream().map(PublicBillResponse::toItem).toList())
                .issuer(Issuer.builder()
                        .name(settings.getCompanyName())
                        .gstin(bill.getSupplierGstin())
                        .address(settings.getCompanyAddress())
                        .email(settings.getCompanyEmail())
                        .phone(settings.getCompanyPhone())
                        .build())
                .build();
    }

    private static Item toItem(BillItem i) {
        return Item.builder()
                .name(i.getName())
                .hsnSac(i.getHsnSac())
                .quantity(i.getQuantity())
                .price(i.getPrice())
                .amount(i.getAmount())
                .build();
    }
}
