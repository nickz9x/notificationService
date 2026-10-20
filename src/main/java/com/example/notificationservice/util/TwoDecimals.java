package com.example.notificationservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TwoDecimals {

    private TwoDecimals() {
    }

    public static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return null;
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }
}