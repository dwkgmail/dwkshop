package com.dwkshop.backend.product;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceFormatter {

    private PriceFormatter() {
    }

    public static String formatCents(Integer cents) {
        if (cents == null) {
            return null;
        }
        return BigDecimal.valueOf(cents)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY)
            .stripTrailingZeros()
            .toPlainString();
    }
}
