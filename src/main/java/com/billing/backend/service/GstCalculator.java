package com.billing.backend.service;

import com.billing.backend.dto.GstBreakdown;
import com.billing.backend.entity.BillItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Single source of truth for bill totals. Totals sent by the client are never trusted.
 */
@Component
public class GstCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    public BigDecimal lineAmount(BillItem item) {
        return item.getQuantity().multiply(item.getPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @param gstRate percentage (e.g. 18.00), or null for No GST
     */
    public GstBreakdown calculate(List<BillItem> items, BigDecimal gstRate,
                                  String supplierStateCode, String placeOfSupplyStateCode) {

        // Sum of the rounded line amounts, so the printed lines always add up to the subtotal
        BigDecimal subtotal = items.stream()
                .map(this::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        boolean interState = !Objects.equals(supplierStateCode, placeOfSupplyStateCode);

        if (gstRate == null) {
            return new GstBreakdown(subtotal, null, interState,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, subtotal);
        }

        BigDecimal tax = subtotal.multiply(gstRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;

        if (interState) {
            igst = tax;
        } else {
            cgst = tax.divide(TWO, 2, RoundingMode.HALF_UP);
            sgst = tax.subtract(cgst);                      // cgst + sgst == tax exactly
        }
        return new GstBreakdown(subtotal, gstRate, interState, cgst, sgst, igst, tax, subtotal.add(tax));
    }
}
