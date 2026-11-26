package com.ledger.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Deterministic BigDecimal arithmetic with explicit currency rounding rules.
 * Monetary storage uses scale 4 in SQL; transactional rounding applies currency minor units
 * (0 for IDR/JPY, 2 for USD/EUR/SGD/GBP/AUD) with HALF_UP and normalizes API output to scale 2.
 */
public final class MoneyUtil {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    public static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    public static final int RATE_SCALE = 8;

    private static final Map<String, Integer> CURRENCY_MINOR_UNITS = Map.of(
            "IDR", 0,
            "JPY", 0,
            "USD", 2,
            "EUR", 2,
            "SGD", 2,
            "GBP", 2,
            "AUD", 2
    );

    private MoneyUtil() {
    }

    public static int minorUnits(String currencyCode) {
        if (currencyCode == null) {
            return 2;
        }
        return CURRENCY_MINOR_UNITS.getOrDefault(currencyCode.toUpperCase(), 2);
    }

    public static BigDecimal round(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return ZERO;
        }
        int minor = minorUnits(currencyCode);
        BigDecimal rounded = amount.setScale(minor, RoundingMode.HALF_UP);
        return rounded.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal convertToBase(BigDecimal foreignAmount, BigDecimal exchangeRate, String baseCurrency) {
        if (foreignAmount == null) {
            return ZERO;
        }
        BigDecimal rate = exchangeRate == null ? BigDecimal.ONE : exchangeRate;
        return round(foreignAmount.multiply(rate), baseCurrency);
    }

    public static BigDecimal percentageOf(BigDecimal base, BigDecimal percentRate, String currencyCode) {
        if (base == null || percentRate == null || percentRate.compareTo(BigDecimal.ZERO) == 0) {
            return ZERO;
        }
        BigDecimal raw = base.multiply(percentRate).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
        return round(raw, currencyCode);
    }

    public static BigDecimal extractInclusiveTax(BigDecimal grossAmount, BigDecimal taxRate, String currencyCode) {
        if (grossAmount == null || taxRate == null || taxRate.compareTo(BigDecimal.ZERO) == 0) {
            return ZERO;
        }
        BigDecimal divisor = ONE_HUNDRED.add(taxRate);
        BigDecimal net = grossAmount.multiply(ONE_HUNDRED).divide(divisor, 8, RoundingMode.HALF_UP);
        BigDecimal roundedNet = round(net, currencyCode);
        return round(grossAmount.subtract(roundedNet), currencyCode);
    }

    public static String toMoneyString(BigDecimal amount) {
        return normalize(amount).toPlainString();
    }

    public static boolean isZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNegative(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) < 0;
    }
}
