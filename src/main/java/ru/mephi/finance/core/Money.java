package ru.mephi.finance.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Money() { }

    public static BigDecimal parse(String value, boolean allowZero) {
        if (value == null || !value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Сумма должна быть числом с не более чем двумя знаками после точки");
        }
        return validate(new BigDecimal(value), allowZero);
    }

    public static BigDecimal validate(BigDecimal value, boolean allowZero) {
        if (value == null || value.scale() > 2 || value.signum() < 0 || (!allowZero && value.signum() == 0)) {
            throw new IllegalArgumentException("Сумма должна быть " + (allowZero ? "неотрицательной" : "положительной")
                    + " и иметь не более двух знаков после точки");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
