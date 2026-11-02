package ru.mephi.finance.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import ru.mephi.finance.core.Type;
import ru.mephi.finance.core.Wallet;

class CsvCodecTest {
    private final CsvCodec codec = new CsvCodec();

    @Test
    void roundTripPreservesQuotesCommasNewlinesUnicodeAndBudgets() {
        Wallet wallet = Wallet.empty().addCategory(Type.EXPENSE, "Еда, \"кафе\"\nдом")
                .setBudget("Еда, \"кафе\"\nдом", "10")
                .add(Type.EXPENSE, "Еда, \"кафе\"\nдом", "2.50", LocalDate.of(2026, 2, 1),
                        "Один, два\n\"три\"\r\n💰");
        assertEquals(wallet, codec.decode(codec.encode(wallet)));
    }

    @Test
    void repeatedImportReplacesInsteadOfDuplicating() {
        Wallet wallet = Wallet.empty().addCategory(Type.INCOME, "Доход")
                .add(Type.INCOME, "Доход", "5", LocalDate.now(), "");
        String csv = codec.encode(wallet);
        assertEquals(1, codec.decode(codec.encode(codec.decode(csv))).entries().size());
    }

    @Test
    void invalidMoneyAndBrokenReferencesAreRejected() {
        Wallet wallet = Wallet.empty().addCategory(Type.EXPENSE, "Еда")
                .add(Type.EXPENSE, "Еда", "1.23", LocalDate.now(), "");
        String csv = codec.encode(wallet);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(csv.replace("1.23", "-1.23")));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(csv.lines().filter(line -> !line.startsWith("CATEGORY")).reduce("", (a, b) -> a + b + "\n")));
    }

    @Test
    void malformedCsvAndDuplicateIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("FINANCE,1\n\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("FINANCE,1\n\"x\"junk\n"));
        Wallet wallet = Wallet.empty().addCategory(Type.INCOME, "Доход");
        String csv = codec.encode(wallet);
        String category = csv.lines().filter(line -> line.startsWith("CATEGORY")).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> codec.decode(csv + category + "\n"));
    }
}
