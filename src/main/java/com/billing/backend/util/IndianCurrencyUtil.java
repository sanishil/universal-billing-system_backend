package com.billing.backend.util;

import org.springframework.stereotype.Component;

/**
 * IndianCurrencyUtil — converts numbers to Indian Rupee words.
 *
 * This is the Java equivalent of the frontend's numberToIndianWords()
 * function from currency-utils.ts.
 *
 * Indian number system uses:
 *   Hundred   = 100
 *   Thousand  = 1,000
 *   Lakh      = 1,00,000     (100 thousand)
 *   Crore     = 1,00,00,000  (10 million)
 *
 * Examples:
 *   182900      → "Rupees One Lakh Eighty Two Thousand Nine Hundred Only"
 *   76700       → "Rupees Seventy Six Thousand Seven Hundred Only"
 *   12450000    → "Rupees One Crore Twenty Four Lakh Fifty Thousand Only"
 *   1500.50     → "Rupees One Thousand Five Hundred and Fifty Paise Only"
 *
 * @Component → Spring will manage this as a bean, inject it where needed
 */
@Component
public class IndianCurrencyUtil {

    // Words for ones: index 0 = "", index 1 = "One", index 2 = "Two", etc.
    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
        "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
        "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    // Words for tens: index 0 = "", index 1 = "", index 2 = "Twenty", etc.
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    };

    /**
     * Convert a decimal amount to Indian words format.
     *
     * @param amount The amount in INR (e.g. 76700.50)
     * @return Human-readable Indian words (e.g. "Rupees Seventy Six Thousand Seven Hundred Only")
     */
    public String numberToWords(double amount) {
        if (amount == 0) {
            return "Rupees Zero Only";
        }

        // Handle negative amounts
        String prefix = "";
        if (amount < 0) {
            prefix = "Minus ";
            amount = Math.abs(amount);
        }

        // Split into rupees and paise (like dollars and cents)
        long rupees = (long) amount;
        int paise = (int) Math.round((amount - rupees) * 100);

        StringBuilder result = new StringBuilder();
        result.append(prefix).append("Rupees ");
        result.append(convertRupees(rupees));

        // Append paise if present
        if (paise > 0) {
            result.append(" and ").append(convertBelow100(paise)).append(" Paise");
        }

        result.append(" Only");
        return result.toString().trim().replaceAll("\\s+", " ");
    }

    /**
     * Convert rupee amount using Indian system: Crore > Lakh > Thousand > Hundred.
     */
    private String convertRupees(long n) {
        if (n == 0) return "Zero";

        StringBuilder words = new StringBuilder();

        // Crore: 1,00,00,000
        if (n >= 10000000) {
            words.append(convertBelow100((int) (n / 10000000))).append(" Crore ");
            n = n % 10000000;
        }

        // Lakh: 1,00,000
        if (n >= 100000) {
            words.append(convertBelow100((int) (n / 100000))).append(" Lakh ");
            n = n % 100000;
        }

        // Thousand: 1,000
        if (n >= 1000) {
            words.append(convertBelow100((int) (n / 1000))).append(" Thousand ");
            n = n % 1000;
        }

        // Hundred: 100
        if (n >= 100) {
            words.append(ONES[(int) (n / 100)]).append(" Hundred ");
            n = n % 100;
        }

        // Remaining below 100
        if (n > 0) {
            words.append(convertBelow100((int) n));
        }

        return words.toString().trim();
    }

    /**
     * Convert any number below 100 to words.
     * Uses ONES for 1-19, combines TENS + ONES for 20-99.
     */
    private String convertBelow100(int n) {
        if (n < 20) {
            return ONES[n];
        }
        String tens = TENS[n / 10];
        String ones = ONES[n % 10];
        if (!ones.isEmpty()) {
            return tens + " " + ones;
        }
        return tens;
    }

    /**
     * Format a number with Indian comma system (lakhs/crores).
     * e.g. 1234567.89 → "12,34,567.89"
     * e.g. 100000     → "1,00,000.00"
     */
    public String formatIndianCurrency(double amount) {
        // Split into whole and decimal parts
        long wholePart = (long) amount;
        int decimalPart = (int) Math.round((amount - wholePart) * 100);

        // Format the whole part with Indian comma placement
        String wholeStr = Long.toString(wholePart);
        String formatted = formatWithIndianCommas(wholeStr);

        // Always show 2 decimal places
        return formatted + "." + String.format("%02d", decimalPart);
    }

    /**
     * Applies Indian comma placement:
     * Last 3 digits, then every 2 digits from right to left.
     * e.g. "1234567" → "12,34,567"
     */
    private String formatWithIndianCommas(String number) {
        if (number.length() <= 3) {
            return number;
        }

        // Last 3 digits stay as-is
        String lastThree = number.substring(number.length() - 3);
        String remaining = number.substring(0, number.length() - 3);

        // Insert comma every 2 digits from right for the remaining part
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = remaining.length() - 1; i >= 0; i--) {
            if (count == 2) {
                sb.insert(0, ',');
                count = 0;
            }
            sb.insert(0, remaining.charAt(i));
            count++;
        }

        return sb + "," + lastThree;
    }
}
