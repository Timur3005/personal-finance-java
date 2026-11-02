package ru.mephi.finance.infra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ru.mephi.finance.core.Category;
import ru.mephi.finance.core.Entry;
import ru.mephi.finance.core.Money;
import ru.mephi.finance.core.Type;
import ru.mephi.finance.core.Wallet;

public final class CsvCodec {
    public String encode(Wallet wallet) {
        StringBuilder result = new StringBuilder("FINANCE,1\r\n");
        for (Category category : wallet.categories()) {
            row(result, List.of("CATEGORY", category.id().toString(), category.type().name(), category.name(),
                    category.budget() == null ? "" : category.budget().toPlainString()));
        }
        for (Entry entry : wallet.entries()) {
            row(result, List.of("ENTRY", entry.id().toString(), entry.type().name(), entry.categoryId().toString(),
                    entry.amount().toPlainString(), entry.date().toString(), entry.description(),
                    entry.transferId() == null ? "" : entry.transferId().toString(),
                    entry.counterparty() == null ? "" : entry.counterparty()));
        }
        return result.toString();
    }

    private void row(StringBuilder output, List<String> fields) {
        output.append(fields.stream().map(value -> value.matches("[^,\"\\r\\n]*") ? value
                : "\"" + value.replace("\"", "\"\"") + "\"").collect(java.util.stream.Collectors.joining(",")))
                .append("\r\n");
    }

    public Wallet decode(String text) {
        try {
            List<List<String>> rows = parse(text);
            if (rows.isEmpty() || !rows.get(0).equals(List.of("FINANCE", "1"))) {
                throw new IllegalArgumentException("Неизвестный заголовок CSV");
            }
            List<Category> categories = new ArrayList<>();
            List<Entry> entries = new ArrayList<>();
            for (List<String> row : rows.subList(1, rows.size())) {
                if (row.size() == 5 && row.get(0).equals("CATEGORY")) {
                    BigDecimal budget = row.get(4).isEmpty() ? null : Money.parse(row.get(4), true);
                    categories.add(new Category(UUID.fromString(row.get(1)), Type.valueOf(row.get(2)), row.get(3), budget));
                } else if (row.size() == 9 && row.get(0).equals("ENTRY")) {
                    entries.add(new Entry(UUID.fromString(row.get(1)), Type.valueOf(row.get(2)), UUID.fromString(row.get(3)),
                            Money.parse(row.get(4), false), LocalDate.parse(row.get(5)), row.get(6),
                            row.get(7).isEmpty() ? null : UUID.fromString(row.get(7)), row.get(8).isEmpty() ? null : row.get(8)));
                } else {
                    throw new IllegalArgumentException("Неверная запись CSV");
                }
            }
            return new Wallet(categories, entries);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("Неверная дата в CSV", e);
        }
    }

    private List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        boolean started = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        value.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closed = true;
                    }
                } else {
                    value.append(current);
                }
            } else if (current == ',' || current == '\r' || current == '\n') {
                row.add(value.toString());
                value.setLength(0);
                closed = false;
                started = false;
                if (current != ',') {
                    rows.add(List.copyOf(row));
                    row.clear();
                    if (current == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                }
            } else if (current == '"' && !started && !closed) {
                quoted = true;
                started = true;
            } else {
                if (closed || current == '"') {
                    throw new IllegalArgumentException("Неверное экранирование CSV");
                }
                value.append(current);
                started = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Незакрытая кавычка CSV");
        }
        if (started || closed || !row.isEmpty()) {
            row.add(value.toString());
            rows.add(List.copyOf(row));
        }
        return rows;
    }
}
