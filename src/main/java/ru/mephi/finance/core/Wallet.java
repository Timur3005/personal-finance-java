package ru.mephi.finance.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record Wallet(List<Category> categories, List<Entry> entries) {
    public Wallet {
        categories = List.copyOf(categories);
        entries = List.copyOf(entries);
        Set<UUID> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Category category : categories) {
            if (!ids.add(category.id()) || !names.add(category.type() + "\u0000" + category.name())) {
                throw new IllegalArgumentException("Повтор категории");
            }
        }
        Set<UUID> entryIds = new HashSet<>();
        Set<UUID> transferIds = new HashSet<>();
        for (Entry entry : entries) {
            if (!entryIds.add(entry.id())) {
                throw new IllegalArgumentException("Повтор идентификатора операции");
            }
            Category category = find(categories, entry.categoryId());
            if (category.type() != entry.type()) {
                throw new IllegalArgumentException("Тип операции не совпадает с категорией");
            }
            if (entry.transferId() != null && !transferIds.add(entry.transferId())) {
                throw new IllegalArgumentException("Повтор перевода");
            }
        }
    }

    public static Wallet empty() {
        return new Wallet(List.of(), List.of());
    }

    private static Category find(List<Category> values, UUID id) {
        return values.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная категория операции: " + id));
    }

    public Category category(UUID id) {
        return find(categories, id);
    }

    public Category category(Type type, String name) {
        return categories.stream().filter(c -> c.type() == type && c.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная категория: " + name));
    }

    public Wallet addCategory(Type type, String name) {
        List<Category> next = new ArrayList<>(categories);
        next.add(new Category(UUID.randomUUID(), type, name, null));
        return new Wallet(next, entries);
    }

    public Wallet renameCategory(Type type, String oldName, String newName) {
        Category old = category(type, oldName);
        return replaceCategory(new Category(old.id(), type, newName, old.budget()));
    }

    private Wallet replaceCategory(Category replacement) {
        return new Wallet(categories.stream().map(c -> c.id().equals(replacement.id()) ? replacement : c).toList(), entries);
    }

    public Wallet deleteCategory(Type type, String name) {
        Category old = category(type, name);
        if (entries.stream().anyMatch(e -> e.categoryId().equals(old.id()))) {
            throw new IllegalArgumentException("Категория используется в операциях и не может быть удалена");
        }
        return new Wallet(categories.stream().filter(c -> !c.id().equals(old.id())).toList(), entries);
    }

    public Wallet setBudget(String name, String amount) {
        Category old = category(Type.EXPENSE, name);
        return replaceCategory(new Category(old.id(), old.type(), old.name(), Money.parse(amount, true)));
    }

    public Wallet removeBudget(String name) {
        Category old = category(Type.EXPENSE, name);
        return replaceCategory(new Category(old.id(), old.type(), old.name(), null));
    }

    public Wallet add(Type type, String name, String amount, LocalDate date, String description) {
        return append(new Entry(UUID.randomUUID(), type, category(type, name).id(),
                Money.parse(amount, false), date, description, null, null));
    }

    public Wallet append(Entry entry) {
        List<Entry> next = new ArrayList<>(entries);
        next.add(entry);
        return new Wallet(categories, next);
    }

    public BigDecimal balance() {
        return total(entries, Type.INCOME).subtract(total(entries, Type.EXPENSE));
    }

    private static BigDecimal total(List<Entry> values, Type type) {
        return values.stream().filter(e -> e.type() == type).map(Entry::amount).reduce(Money.ZERO, BigDecimal::add);
    }

    private BigDecimal spent(UUID categoryId) {
        return entries.stream().filter(e -> e.categoryId().equals(categoryId)).map(Entry::amount)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    public List<Report.BudgetStatus> budgets() {
        return categories.stream().filter(c -> c.budget() != null)
                .map(c -> new Report.BudgetStatus(c, spent(c.id()), c.budget().subtract(spent(c.id())))).toList();
    }

    public List<String> warnings() {
        List<String> result = new ArrayList<>();
        for (Report.BudgetStatus budget : budgets()) {
            String name = budget.category().name();
            if (budget.remaining().signum() < 0) {
                result.add("Бюджет «" + name + "» превышен");
            } else if (budget.remaining().signum() == 0) {
                result.add("Бюджет «" + name + "» исчерпан");
            } else if (budget.spent().compareTo(budget.category().budget().multiply(new BigDecimal("0.80"))) >= 0) {
                result.add("Бюджет «" + name + "»: использовано не менее 80%");
            }
        }
        if (balance().signum() < 0) {
            result.add("Расходы больше доходов; баланс отрицательный");
        } else if (balance().signum() == 0) {
            result.add("Нулевой баланс");
        }
        return List.copyOf(result);
    }

    public Report report(ReportFilter filter) {
        List<String> unknown = filter.categories().stream().filter(name -> categories.stream()
                .noneMatch(c -> c.name().equals(name) && (filter.type() == null || c.type() == filter.type())))
                .sorted().toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Неизвестные категории: " + String.join(", ", unknown));
        }
        List<Entry> selected = entries.stream().filter(e -> filter.type() == null || e.type() == filter.type())
                .filter(e -> filter.categories().isEmpty() || filter.categories().contains(category(e.categoryId()).name()))
                .filter(e -> filter.from() == null || !e.date().isBefore(filter.from()))
                .filter(e -> filter.to() == null || !e.date().isAfter(filter.to())).toList();
        List<Report.CategoryTotal> totals = categories.stream()
                .filter(c -> filter.type() == null || c.type() == filter.type())
                .filter(c -> filter.categories().isEmpty() || filter.categories().contains(c.name()))
                .map(c -> new Report.CategoryTotal(c, selected.stream().filter(e -> e.categoryId().equals(c.id()))
                        .map(Entry::amount).reduce(Money.ZERO, BigDecimal::add))).toList();
        BigDecimal income = total(selected, Type.INCOME);
        BigDecimal expense = total(selected, Type.EXPENSE);
        return new Report(income, expense, income.subtract(expense), totals, budgets(), warnings(), selected);
    }
}
