package ru.mephi.finance.core;

import java.util.LinkedHashMap;
import java.util.Map;

public record State(Map<String, User> users) {
    public State {
        users = Map.copyOf(users);
        for (Map.Entry<String, User> entry : users.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().login())) {
                throw new IllegalArgumentException("Неверный ключ пользователя");
            }
        }
    }

    public static State empty() {
        return new State(Map.of());
    }

    public State withUser(User user) {
        Map<String, User> next = new LinkedHashMap<>(users);
        next.put(user.login(), user);
        return new State(next);
    }
}
