package com.billing.backend.dto;

import java.math.BigDecimal;

/**
 * Result of GstCalculator. gstRate == null means "No GST" (all tax fields are zero).
 */
public record GstBreakdown(BigDecimal subtotal, BigDecimal gstRate, boolean interState,
                           BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                           BigDecimal tax, BigDecimal total) {
}
