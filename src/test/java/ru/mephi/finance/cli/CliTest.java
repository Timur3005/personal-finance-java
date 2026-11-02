package ru.mephi.finance.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {
    @TempDir Path directory;

    @Test
    void tokenizerSupportsSpacesEscapedQuotesAndRejectsUnclosedQuote() {
        assertEquals(List.of("income", "Подработка дома", "2", "a\"b"),
                CommandLine.parse("income \"Подработка дома\" 2 \"a\\\"b\""));
        assertThrows(IllegalArgumentException.class, () -> CommandLine.parse("income \"open"));
    }

    @Test
    void eofSavesWalletAndNewSessionLoadsIt() {
        Result first = run("register alice\nsecret\nlogin alice\nsecret\n"
                + "category add income \"Основная работа\"\nincome \"Основная работа\" 10.25\n");
        assertEquals(0, first.code());
        Result second = run("login alice\nsecret\nreport\nexit\n");
        assertEquals(0, second.code());
        assertTrue(second.output().contains("10.25"));
    }

    @Test
    void badInputDoesNotEndLoopAndEmptyReportIsExplicit() {
        Result result = run("register alice\nsecret\nlogin alice\nsecret\n"
                + "nonsense\nincome missing abc\nreport\nexit\n");
        assertEquals(0, result.code());
        assertTrue(result.output().contains("Ошибка"));
        assertTrue(result.output().contains("Нет операций"));
    }

    @Test
    void reportCanBeWrittenToFileAndCsvRestoresWallet() throws Exception {
        Path report = directory.resolve("отчёт.txt");
        Path csv = directory.resolve("кошелёк.csv");
        Result result = run("register alice\nsecret\nlogin alice\nsecret\n"
                + "category add expense Еда\nbudget set Еда 10\nexpense Еда 2.50 2026-01-01 обед\n"
                + "report --type expense --categories Еда --from 2026-01-01 --to 2026-01-01 --file \""
                + report + "\"\nexport \"" + csv + "\"\nimport \"" + csv + "\"\nhistory\nexit\n");
        assertEquals(0, result.code());
        assertTrue(Files.readString(report).contains("7.50"));
        assertFalse(Files.readString(csv).contains("secret"));
        assertTrue(result.output().contains("обед"));
    }

    @Test
    void invalidImportDoesNotReplaceActiveWallet() throws Exception {
        Path csv = directory.resolve("bad.csv");
        Files.writeString(csv, "broken");
        Result result = run("register alice\nsecret\nlogin alice\nsecret\n"
                + "category add income Работа\nincome Работа 42\nimport \"" + csv + "\"\nreport\nexit\n");
        assertEquals(0, result.code());
        assertTrue(result.output().contains("Ошибка"));
        assertTrue(result.output().contains("42.00"));
    }

    @Test
    void corruptedStorageExitsNonzeroAndDoesNotOverwrite() throws Exception {
        Files.writeString(directory.resolve("state.bin"), "broken");
        Result result = run("exit\n");
        assertEquals(1, result.code());
        assertEquals("broken", Files.readString(directory.resolve("state.bin")));
    }

    @Test
    void exportAndReportCannotOverwriteStorageFilesOrTheirSymlinks() throws Exception {
        Path alias = directory.resolve("alias.csv");
        Files.createSymbolicLink(alias, directory.resolve("state.bin"));
        Result result = run("register alice\nsecret\nlogin alice\nsecret\nexport \""
                + directory.resolve("state.bin") + "\"\nreport --file \""
                + directory.resolve("finance.lock") + "\"\nexport \"" + alias + "\"\nexit\n");
        assertEquals(0, result.code());
        assertEquals(3, result.output().lines().filter(s -> s.contains("служебный файл")).count());
        assertEquals(0, run("login alice\nsecret\nexit\n").code());
    }

    @Test
    void exportAndReportCreateMissingParentDirectories() throws Exception {
        Path csv = directory.resolve("exports/nested/wallet.csv");
        Path report = directory.resolve("reports/nested/report.txt");
        Result result = run("register alice\nsecret\nlogin alice\nsecret\nexport \"" + csv
                + "\"\nreport --file \"" + report + "\"\nexit\n");
        assertEquals(0, result.code());
        assertTrue(Files.readString(csv).startsWith("FINANCE,1"));
        assertTrue(Files.readString(report).contains("Нет операций"));
    }

    @Test
    void failedEofSaveReturnsFailureWithoutSuccessMessage() {
        StringReader input = new StringReader("") {
            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                Files.createDirectory(directory.resolve("state.bin"));
                return -1;
            }
        };
        StringWriter output = new StringWriter();
        assertEquals(1, Main.run(new String[] {"--data-dir", directory.toString()}, input, new PrintWriter(output, true)));
        assertTrue(output.toString().contains("Ошибка сохранения"));
        assertFalse(output.toString().contains("Данные сохранены"));
    }

    @Test
    void directoryOutputPathsDoNotCrashOrLoseUnsavedWallet() {
        String root = directory.getRoot().toString();
        Result result = run("register alice\nsecret\nlogin alice\nsecret\n"
                + "category add income Работа\nincome Работа 123.45\nexport \"" + root
                + "\"\nreport --file \"" + root + "\"\nexit\n");
        assertEquals(0, result.code());
        assertEquals(2, result.output().lines().filter(s -> s.contains("Ошибка")).count());
        Result restarted = run("login alice\nsecret\nreport\nexit\n");
        assertEquals(0, restarted.code());
        assertTrue(restarted.output().contains("123.45"));
    }

    @Test
    void inlineReportOptionsSelectCategoriesStartingWithDoubleDash() throws IOException {
        Path report = directory.resolve("--report.txt");
        Result result = run("register alice\nsecret\nlogin alice\nsecret\n"
                + "category add expense --Food\ncategory add expense --Taxi\ncategory add expense Other\n"
                + "expense --Food 2\nexpense --Taxi 5\nexpense Other 100\n"
                + "report --type=expense --categories=--Food --categories=--Taxi --file=\"" + report + "\"\nexit\n");
        assertEquals(0, result.code());
        assertFalse(result.output().contains("Ошибка"));
        assertTrue(Files.readString(report).contains("Общий расход: 7.00"));
        assertFalse(Files.readString(report).contains("Other"));
    }

    private Result run(String input) {
        StringWriter output = new StringWriter();
        int code = Main.run(new String[] {"--data-dir", directory.toString()},
                new StringReader(input), new PrintWriter(output, true));
        return new Result(code, output.toString());
    }

    private record Result(int code, String output) { }
}
