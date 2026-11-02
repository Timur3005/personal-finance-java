package ru.mephi.finance.core;

import java.util.Objects;

public record User(String login, String passwordHash, Wallet wallet) {
    public User {
        validateLogin(login);
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Отсутствует хеш пароля");
        }
        Objects.requireNonNull(wallet, "wallet");
    }

    public static void validateLogin(String login) {
        if (login == null || !login.matches("[\\p{L}\\p{N}_.-]{1,64}")) {
            throw new IllegalArgumentException("Логин: 1–64 буквы, цифры, точки, дефисы или подчёркивания");
        }
    }

    public User withWallet(Wallet value) {
        return new User(login, passwordHash, value);
    }
}
