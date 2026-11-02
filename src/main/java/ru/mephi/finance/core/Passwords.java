package ru.mephi.finance.core;

public interface Passwords {
    String hash(char[] password);

    boolean verify(char[] password, String encoded);
}
