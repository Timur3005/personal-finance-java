package ru.mephi.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WalletTest {
    private static final LocalDate DAY = LocalDate.of(2026, 1, 15);

    private Wallet basic() {
        return Wallet.empty().addCategory(Type.INCOME, "Зарплата")
                .addCategory(Type.EXPENSE, "Еда");
    }

    @Test
    void assignmentExampleCalculatesTotalsAndBudgetRemainders() {
        Wallet wallet = basic().addCategory(Type.INCOME, "Подработка")
                .addCategory(Type.EXPENSE, "Развлечения")
                .addCategory(Type.EXPENSE, "Коммунальные услуги")
                .setBudget("Еда", "4000").setBudget("Развлечения", "3000")
                .setBudget("Коммунальные услуги", "4000")
                .add(Type.INCOME, "Зарплата", "60000", DAY, "")
                .add(Type.INCOME, "Подработка", "3000", DAY, "")
                .add(Type.EXPENSE, "Еда", "800", DAY, "")
                .add(Type.EXPENSE, "Развлечения", "3000", DAY, "")
                .add(Type.EXPENSE, "Коммунальные услуги", "4500", DAY, "");
        Report report = wallet.report(ReportFilter.all());
        assertEquals(new BigDecimal("63000.00"), report.income());
        assertEquals(new BigDecimal("8300.00"), report.expense());
        assertEquals(new BigDecimal("54700.00"), report.balance());
        assertEquals(List.of(new BigDecimal("3200.00"), new BigDecimal("0.00"),
                new BigDecimal("-500.00")), report.budgets().stream()
                .map(Report.BudgetStatus::remaining).toList());
    }

    @Test
    void exactDecimalsDoNotLoseCents() {
        Wallet wallet = basic().add(Type.INCOME, "Зарплата", "0.10", DAY, "")
                .add(Type.INCOME, "Зарплата", "0.20", DAY, "");
        assertEquals(new BigDecimal("0.30"), wallet.balance());
    }

    @Test
    void invalidAmountsNeverCreateEntries() {
        Wallet wallet = basic();
        for (String amount : List.of("0", "-1", "1.001", "abc", "NaN", "1e2", "1.000")) {
            assertThrows(IllegalArgumentException.class,
                    () -> wallet.add(Type.EXPENSE, "Еда", amount, DAY, ""), amount);
        }
        assertTrue(wallet.entries().isEmpty());
    }

    @Test
    void unknownCategoryIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> basic().add(Type.EXPENSE, "Нет", "1", DAY, ""));
    }

    @Test
    void sameNameMayExistForDifferentTypesButNotTwiceForSameType() {
        Wallet wallet = basic().addCategory(Type.INCOME, "Еда");
        assertThrows(IllegalArgumentException.class, () -> wallet.addCategory(Type.EXPENSE, "Еда"));
        assertEquals(3, wallet.categories().size());
    }

    @Test
    void renameKeepsHistoryAndBudget() {
        Wallet wallet = basic().setBudget("Еда", "10")
                .add(Type.EXPENSE, "Еда", "3", DAY, "")
                .renameCategory(Type.EXPENSE, "Еда", "Продукты");
        assertEquals("Продукты", wallet.category(wallet.entries().get(0).categoryId()).name());
        assertEquals(new BigDecimal("7.00"), wallet.report(ReportFilter.all()).budgets().get(0).remaining());
    }

    @Test
    void usedCategoryCannotBeDeleted() {
        Wallet wallet = basic().add(Type.EXPENSE, "Еда", "1", DAY, "");
        assertThrows(IllegalArgumentException.class, () -> wallet.deleteCategory(Type.EXPENSE, "Еда"));
    }

    @Test
    void unusedCategoryCanBeDeletedAndBudgetRemoved() {
        Wallet wallet = basic().setBudget("Еда", "0").removeBudget("Еда");
        assertTrue(wallet.report(ReportFilter.all()).budgets().isEmpty());
        assertEquals(1, wallet.deleteCategory(Type.EXPENSE, "Еда").categories().size());
    }

    @Test
    void expensesCanOverdrawAndProduceWarnings() {
        Wallet wallet = basic().add(Type.EXPENSE, "Еда", "5", DAY, "");
        assertEquals(new BigDecimal("-5.00"), wallet.balance());
        assertTrue(wallet.warnings().stream().anyMatch(s -> s.contains("больше доходов")));
    }

    @Test
    void zeroBalanceAndBudgetThresholdsProduceDistinctWarnings() {
        assertTrue(basic().warnings().stream().anyMatch(s -> s.contains("Нулевой баланс")));
        Wallet wallet = basic().setBudget("Еда", "100")
                .add(Type.EXPENSE, "Еда", "80", DAY, "");
        assertTrue(wallet.warnings().stream().anyMatch(s -> s.contains("80%")));
        wallet = wallet.add(Type.EXPENSE, "Еда", "20", DAY, "");
        assertTrue(wallet.warnings().stream().anyMatch(s -> s.contains("исчерпан")));
        wallet = wallet.add(Type.EXPENSE, "Еда", "0.01", DAY, "");
        assertTrue(wallet.warnings().stream().anyMatch(s -> s.contains("превышен")));
    }

    @Test
    void zeroBudgetIsValidAndCannotBeNegative() {
        assertTrue(basic().setBudget("Еда", "0").warnings().stream()
                .anyMatch(s -> s.contains("исчерпан")));
        assertThrows(IllegalArgumentException.class, () -> basic().setBudget("Еда", "-1"));
    }

    @Test
    void reportFiltersAreInclusiveAndBudgetUsesAllHistory() {
        Wallet wallet = basic().addCategory(Type.EXPENSE, "Кино").setBudget("Еда", "10")
                .add(Type.EXPENSE, "Еда", "3", DAY.minusDays(1), "old")
                .add(Type.EXPENSE, "Еда", "2", DAY, "today")
                .add(Type.EXPENSE, "Кино", "4", DAY, "movie")
                .add(Type.INCOME, "Зарплата", "100", DAY, "pay");
        Report report = wallet.report(new ReportFilter(Type.EXPENSE, Set.of("Еда", "Кино"), DAY, DAY));
        assertEquals(new BigDecimal("6.00"), report.expense());
        assertEquals(new BigDecimal("0.00"), report.income());
        assertEquals(2, report.entries().size());
        assertEquals(new BigDecimal("5.00"), report.budgets().get(0).spent());
    }

    @Test
    void reportListsAllUnknownCategoriesAndRejectsReversedDates() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> basic().report(new ReportFilter(null, Set.of("A", "B"), null, null)));
        assertTrue(error.getMessage().contains("A"));
        assertTrue(error.getMessage().contains("B"));
        assertThrows(IllegalArgumentException.class,
                () -> new ReportFilter(null, Set.of(), DAY, DAY.minusDays(1)));
    }

    @Test
    void walletDoesNotExposeMutableCollections() {
        Wallet wallet = basic();
        assertThrows(UnsupportedOperationException.class, () -> wallet.categories().clear());
        assertThrows(UnsupportedOperationException.class, () -> wallet.entries().clear());
    }
}
