package ru.mephi.finance.core;

import java.math.BigDecimal;
import java.util.List;

public record Report(BigDecimal income, BigDecimal expense, BigDecimal balance,
                     List<CategoryTotal> categories, List<BudgetStatus> budgets,
                     List<String> warnings, List<Entry> entries) {
    public Report {
        categories = List.copyOf(categories);
        budgets = List.copyOf(budgets);
        warnings = List.copyOf(warnings);
        entries = List.copyOf(entries);
    }

    public record CategoryTotal(Category category, BigDecimal total) { }

    public record BudgetStatus(Category category, BigDecimal spent, BigDecimal remaining) { }
}
