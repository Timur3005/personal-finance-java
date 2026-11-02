package ru.mephi.finance.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class FinanceService {
    private final Repository repository;
    private final Passwords passwords;
    private State state;
    private String currentLogin;

    public FinanceService(Repository repository, Passwords passwords) throws IOException {
        this.repository = repository;
        this.passwords = passwords;
        state = repository.load();
    }

    public void register(String login, char[] password) throws IOException {
        User.validateLogin(login);
        if (password == null || password.length == 0 || password.length > 1024) {
            throw new IllegalArgumentException("Пароль должен содержать от 1 до 1024 символов");
        }
        if (state.users().containsKey(login)) {
            throw new IllegalArgumentException("Логин уже занят");
        }
        State next = state.withUser(new User(login, passwords.hash(password), Wallet.empty()));
        repository.save(next);
        state = next;
    }

    public void login(String login, char[] password) throws IOException {
        if (currentLogin != null) {
            throw new IllegalArgumentException("Сначала выполните logout");
        }
        State loaded = repository.load();
        User user = loaded.users().get(login);
        if (user == null || !passwords.verify(password, user.passwordHash())) {
            throw new IllegalArgumentException("Неверный логин или пароль");
        }
        state = loaded;
        currentLogin = login;
    }

    public String currentLogin() {
        return currentLogin;
    }

    public Wallet wallet() {
        if (currentLogin == null) {
            throw new IllegalArgumentException("Необходимо войти: login <логин>");
        }
        return state.users().get(currentLogin).wallet();
    }

    public void replaceWallet(Wallet value) {
        wallet();
        state = state.withUser(state.users().get(currentLogin).withWallet(value));
    }

    public void addCategory(Type type, String name) {
        replaceWallet(wallet().addCategory(type, name));
    }

    public void renameCategory(Type type, String oldName, String newName) {
        replaceWallet(wallet().renameCategory(type, oldName, newName));
    }

    public void deleteCategory(Type type, String name) {
        replaceWallet(wallet().deleteCategory(type, name));
    }

    public void setBudget(String name, String amount) {
        replaceWallet(wallet().setBudget(name, amount));
    }

    public void removeBudget(String name) {
        replaceWallet(wallet().removeBudget(name));
    }

    public void add(Type type, String category, String amount, LocalDate date, String description) {
        replaceWallet(wallet().add(type, category, amount, date, description));
    }

    public void save() throws IOException {
        repository.save(state);
    }

    public void logout() throws IOException {
        wallet();
        save();
        currentLogin = null;
    }

    public void transfer(String recipientLogin, String amountText, String description) throws IOException {
        Wallet sender = wallet();
        BigDecimal amount = Money.parse(amountText, false);
        if (currentLogin.equals(recipientLogin)) {
            throw new IllegalArgumentException("Нельзя переводить самому себе");
        }
        User recipient = state.users().get(recipientLogin);
        if (recipient == null) {
            throw new IllegalArgumentException("Получатель не найден: " + recipientLogin);
        }
        if (sender.balance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Недостаточно средств для перевода");
        }
        UUID transferId = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        Wallet outgoing = transferEntry(sender, Type.EXPENSE, amount, date, description, transferId, recipientLogin);
        Wallet incoming = transferEntry(recipient.wallet(), Type.INCOME, amount, date, description, transferId, currentLogin);
        State next = state.withUser(state.users().get(currentLogin).withWallet(outgoing))
                .withUser(recipient.withWallet(incoming));
        // Один снимок фиксирует обе стороны; при ошибке записи состояние памяти не меняется.
        repository.save(next);
        state = next;
    }

    private Wallet transferEntry(Wallet wallet, Type type, BigDecimal amount, LocalDate date,
                                 String description, UUID transferId, String counterparty) {
        String categoryName = "Переводы";
        Wallet prepared = wallet.categories().stream().anyMatch(c -> c.type() == type && c.name().equals(categoryName))
                ? wallet : wallet.addCategory(type, categoryName);
        return prepared.append(new Entry(UUID.randomUUID(), type, prepared.category(type, categoryName).id(),
                amount, date, description, transferId, counterparty));
    }
}
