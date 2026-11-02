package ru.mephi.finance.core;

import java.time.LocalDate;
import java.util.Set;

public record ReportFilter(Type type, Set<String> categories, LocalDate from, LocalDate to) {
    public ReportFilter {
        categories = Set.copyOf(categories);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Начальная дата позже конечной");
        }
    }

    public static ReportFilter all() {
        return new ReportFilter(null, Set.of(), null, null);
    }
}
