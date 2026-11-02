package ru.mephi.finance.cli;

import java.util.ArrayList;
import java.util.List;

public final class CommandLine {
    private CommandLine() { }

    public static List<String> parse(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        boolean started = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '\\' && i + 1 < line.length()
                    && (line.charAt(i + 1) == '"' || line.charAt(i + 1) == '\\')) {
                token.append(line.charAt(++i));
                started = true;
            } else if (current == '"') {
                quoted = !quoted;
                started = true;
            } else if (Character.isWhitespace(current) && !quoted) {
                if (started) {
                    result.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
            } else {
                token.append(current);
                started = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Незакрытая кавычка");
        }
        if (started) {
            result.add(token.toString());
        }
        return List.copyOf(result);
    }
}
