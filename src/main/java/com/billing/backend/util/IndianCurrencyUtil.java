package com.billing.backend.util;

import org.springframework.stereotype.Component;

@Component
public class IndianCurrencyUtil {

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven",
        "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
        "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    };

    public String numberToWords(double amount) {
        if (amount == 0) {
            return "Rupees Zero Only";
        }

        String prefix = "";
        if (amount < 0) {
            prefix = "Minus ";
            amount = Math.abs(amount);
        }

        long rupees = (long) amount;
        int paise = (int) Math.round((amount - rupees) * 100);

        StringBuilder result = new StringBuilder();
        result.append(prefix).append("Rupees ");
        result.append(convertRupees(rupees));

        if (paise > 0) {
            result.append(" and ").append(convertBelow100(paise)).append(" Paise");
        }

        result.append(" Only");
        return result.toString().trim().replaceAll("\\s+", " ");
    }

    private String convertRupees(long n) {
        if (n == 0) return "Zero";

        StringBuilder words = new StringBuilder();

        if (n >= 10000000) {
            words.append(convertBelow100((int) (n / 10000000))).append(" Crore ");
            n = n % 10000000;
        }

        if (n >= 100000) {
            words.append(convertBelow100((int) (n / 100000))).append(" Lakh ");
            n = n % 100000;
        }

        if (n >= 1000) {
            words.append(convertBelow100((int) (n / 1000))).append(" Thousand ");
            n = n % 1000;
        }

        if (n >= 100) {
            words.append(ONES[(int) (n / 100)]).append(" Hundred ");
            n = n % 100;
        }

        if (n > 0) {
            words.append(convertBelow100((int) n));
        }

        return words.toString().trim();
    }

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

    public String formatIndianCurrency(double amount) {
        long wholePart = (long) amount;
        int decimalPart = (int) Math.round((amount - wholePart) * 100);

        String wholeStr = Long.toString(wholePart);
        String formatted = formatWithIndianCommas(wholeStr);

        return formatted + "." + String.format("%02d", decimalPart);
    }

    private String formatWithIndianCommas(String number) {
        if (number.length() <= 3) {
            return number;
        }

        String lastThree = number.substring(number.length() - 3);
        String remaining = number.substring(0, number.length() - 3);

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
