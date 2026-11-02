package ru.mephi.finance.cli;

import ru.mephi.finance.core.Entry;
import ru.mephi.finance.core.Report;
import ru.mephi.finance.core.Type;
import ru.mephi.finance.core.Wallet;

public final class ReportFormatter {
    public String report(Report report) {
        StringBuilder text = new StringBuilder();
        text.append("Общий доход: ").append(report.income().toPlainString()).append(" руб.\n");
        text.append("Общий расход: ").append(report.expense().toPlainString()).append(" руб.\n");
        text.append("Баланс по выбранным операциям: ").append(report.balance().toPlainString()).append(" руб.\n");
        if (report.entries().isEmpty()) {
            text.append("Нет операций по выбранным условиям.\n");
        }
        text.append("Суммы по категориям:\n");
        for (Report.CategoryTotal category : report.categories()) {
            text.append(label(category.category().type())).append(" / ").append(category.category().name())
                    .append(": ").append(category.total().toPlainString()).append('\n');
        }
        text.append("Бюджеты за всю историю (бюджет / потрачено / остаток):\n");
        for (Report.BudgetStatus budget : report.budgets()) {
            text.append(budget.category().name()).append(": ").append(budget.category().budget().toPlainString())
                    .append(" / ").append(budget.spent().toPlainString()).append(" / ")
                    .append(budget.remaining().toPlainString()).append('\n');
        }
        for (String warning : report.warnings()) {
            text.append("Предупреждение: ").append(warning).append('\n');
        }
        return text.toString();
    }

    public String history(Wallet wallet) {
        if (wallet.entries().isEmpty()) {
            return "Нет операций.\n";
        }
        StringBuilder text = new StringBuilder();
        for (Entry entry : wallet.entries()) {
            text.append(entry.id()).append(" | ").append(entry.date()).append(" | ").append(label(entry.type()))
                    .append(" | ").append(wallet.category(entry.categoryId()).name()).append(" | ")
                    .append(entry.amount().toPlainString()).append(" | ").append(entry.description());
            if (entry.transferId() != null) {
                text.append(" | перевод ").append(entry.transferId()).append(" | контрагент ").append(entry.counterparty());
            }
            text.append('\n');
        }
        return text.toString();
    }

    private String label(Type type) {
        return type == Type.INCOME ? "Доход" : "Расход";
    }
}
