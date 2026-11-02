package ru.mephi.finance.core;

public enum Type {
    INCOME, EXPENSE;

    public static Type parse(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "income" -> INCOME;
            case "expense" -> EXPENSE;
            default -> throw new IllegalArgumentException("Тип должен быть income или expense");
        };
    }
}
