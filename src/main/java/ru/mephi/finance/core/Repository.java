package ru.mephi.finance.core;

import java.io.IOException;

public interface Repository extends AutoCloseable {
    State load() throws IOException;

    void save(State state) throws IOException;

    @Override
    default void close() throws IOException { }
}
