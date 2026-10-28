package com.billing.backend.service;

import com.billing.backend.dto.GstBreakdown;
import com.billing.backend.entity.BillItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GstCalculatorTest {

    private final GstCalculator calculator = new GstCalculator();

    private static BillItem item(String qty, String price) {
        return BillItem.builder().name("x").quantity(new BigDecimal(qty)).price(new BigDecimal(price)).build();
    }

    @Test
    void noGstHasZeroTaxAndTotalEqualsSubtotal() {
        GstBreakdown b = calculator.calculate(List.of(item("2", "5000")), null, "29", "29");

        assertNull(b.gstRate());
        assertEquals(new BigDecimal("10000.00"), b.subtotal());
        assertEquals(0, b.tax().signum());
        assertEquals(0, b.cgst().signum());
        assertEquals(0, b.sgst().signum());
        assertEquals(0, b.igst().signum());
        assertEquals(b.subtotal(), b.total());
    }

    @Test
    void intraStateSplitsIntoCgstAndSgstThatSumExactlyToTax() {
        // 0.05 × 18% = 0.009 → tax 0.01; halves must not both round up to 0.01
        GstBreakdown b = calculator.calculate(List.of(item("1", "0.05")), new BigDecimal("18"), "29", "29");

        assertFalse(b.interState());
        assertEquals(new BigDecimal("0.01"), b.tax());
        assertEquals(b.tax(), b.cgst().add(b.sgst()));
        assertEquals(0, b.igst().signum());
    }

    @Test
    void interStateUsesIgstOnly() {
        GstBreakdown b = calculator.calculate(List.of(item("3", "333.33")), new BigDecimal("18"), "29", "27");

        assertTrue(b.interState());
        assertEquals(new BigDecimal("999.99"), b.subtotal());
        assertEquals(new BigDecimal("180.00"), b.igst());
        assertEquals(b.tax(), b.igst());
        assertEquals(0, b.cgst().signum());
        assertEquals(new BigDecimal("1179.99"), b.total());
    }
}
