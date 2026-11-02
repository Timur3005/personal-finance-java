package ru.mephi.finance.core;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record Category(UUID id, Type type, String name, BigDecimal budget) {
    public Category {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (name == null || name.isBlank() || !name.equals(name.strip()) || name.length() > 1000) {
            throw new IllegalArgumentException("Название категории должно содержать от 1 до 1000 символов без пробелов по краям");
        }
        if (budget != null) {
            if (type != Type.EXPENSE) {
                throw new IllegalArgumentException("Бюджет доступен только расходной категории");
            }
            budget = Money.validate(budget, true);
        }
    }
}
