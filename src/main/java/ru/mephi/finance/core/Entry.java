package ru.mephi.finance.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Entry(UUID id, Type type, UUID categoryId, BigDecimal amount, LocalDate date,
                    String description, UUID transferId, String counterparty) {
    public Entry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(date, "date");
        amount = Money.validate(amount, false);
        if (description == null || description.length() > 10000) {
            throw new IllegalArgumentException("Описание должно содержать не более 10000 символов");
        }
        if ((transferId == null) != (counterparty == null)
                || (counterparty != null && counterparty.isBlank())) {
            throw new IllegalArgumentException("Неверные реквизиты перевода");
        }
    }
}
