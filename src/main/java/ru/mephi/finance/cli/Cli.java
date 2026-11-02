package ru.mephi.finance.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ru.mephi.finance.core.FinanceService;
import ru.mephi.finance.core.Report;
import ru.mephi.finance.core.ReportFilter;
import ru.mephi.finance.core.Type;
import ru.mephi.finance.infra.CsvCodec;

final class Cli {
    private final FinanceService service;
    private final BufferedReader input;
    private final PrintWriter output;
    private final PasswordInput passwordInput;
    private final OutputFiles outputFiles;
    private final CsvCodec csv = new CsvCodec();
    private final ReportFormatter formatter = new ReportFormatter();
    private boolean ioFailure;

    Cli(FinanceService service, BufferedReader input, PrintWriter output, PasswordInput passwordInput, Path dataDirectory) {
        this.service = service;
        this.input = input;
        this.output = output;
        this.passwordInput = passwordInput;
        outputFiles = new OutputFiles(dataDirectory);
    }

    int run() throws IOException {
        output.println("Личные финансы. Введите help для списка команд.");
        while (true) {
            output.print(service.currentLogin() == null ? "> " : service.currentLogin() + "> ");
            output.flush();
            String line = input.readLine();
            if (line == null) {
                return finish();
            }
            try {
                List<String> args = CommandLine.parse(line);
                if (!args.isEmpty() && execute(args)) {
                    return ioFailure ? 1 : 0;
                }
            } catch (IllegalArgumentException | DateTimeException e) {
                output.println("Ошибка: " + e.getMessage());
            } catch (IOException e) {
                ioFailure = true;
                output.println("Ошибка ввода/вывода: " + e.getMessage() + ". Команда не завершена; можно повторить.");
            }
        }
    }

    private int finish() {
        try {
            service.save();
            output.println("Данные сохранены.");
            return ioFailure ? 1 : 0;
        } catch (IOException e) {
            output.println("Ошибка сохранения: " + e.getMessage());
            return 1;
        }
    }

    private boolean execute(List<String> args) throws IOException {
        String command = args.get(0);
        switch (command) {
            case "help" -> {
                count(args, 1, 1);
                help();
            }
            case "register", "login" -> authenticate(args);
            case "logout" -> {
                count(args, 1, 1);
                service.logout();
                output.println("Данные сохранены. Вы вышли из аккаунта.");
            }
            case "exit" -> {
                count(args, 1, 1);
                service.save();
                output.println("Данные сохранены.");
                return true;
            }
            case "category" -> category(args);
            case "budget" -> budget(args);
            case "income", "expense" -> add(args);
            case "transfer" -> {
                count(args, 3, Integer.MAX_VALUE);
                service.transfer(args.get(1), args.get(2), join(args, 3));
                changed();
            }
            case "report" -> report(args);
            case "history" -> {
                count(args, 1, 1);
                output.print(formatter.history(service.wallet()));
            }
            case "export" -> {
                count(args, 2, 2);
                outputFiles.write(args.get(1), csv.encode(service.wallet()));
                output.println("Кошелёк экспортирован.");
            }
            case "import" -> {
                count(args, 2, 2);
                service.wallet();
                String text = Files.readString(Path.of(args.get(1)), StandardCharsets.UTF_8);
                service.replaceWallet(csv.decode(text));
                changed();
            }
            default -> throw new IllegalArgumentException("Неизвестная команда. Введите help");
        }
        output.flush();
        return false;
    }

    private void authenticate(List<String> args) throws IOException {
        count(args, 2, 2);
        output.print("Пароль: ");
        output.flush();
        char[] password = passwordInput.read();
        if (password == null) {
            throw new IllegalArgumentException("Пароль не введён");
        }
        try {
            if (args.get(0).equals("register")) {
                service.register(args.get(1), password);
                output.println("Регистрация выполнена. Войдите командой login.");
            } else {
                service.login(args.get(1), password);
                output.println("Вход выполнен.");
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void category(List<String> args) {
        count(args, 2, 5);
        switch (args.get(1)) {
            case "list" -> {
                count(args, 2, 2);
                if (service.wallet().categories().isEmpty()) {
                    output.println("Нет категорий.");
                }
                service.wallet().categories().forEach(c -> output.println(c.type().name().toLowerCase(java.util.Locale.ROOT)
                        + " | " + c.name() + (c.budget() == null ? "" : " | бюджет " + c.budget().toPlainString())));
                return;
            }
            case "add" -> {
                count(args, 4, 4);
                service.addCategory(Type.parse(args.get(2)), args.get(3));
            }
            case "rename" -> {
                count(args, 5, 5);
                service.renameCategory(Type.parse(args.get(2)), args.get(3), args.get(4));
            }
            case "delete" -> {
                count(args, 4, 4);
                service.deleteCategory(Type.parse(args.get(2)), args.get(3));
            }
            default -> throw new IllegalArgumentException("category: add, rename, delete или list");
        }
        changed();
    }

    private void budget(List<String> args) {
        count(args, 3, 4);
        switch (args.get(1)) {
            case "set" -> {
                count(args, 4, 4);
                service.setBudget(args.get(2), args.get(3));
            }
            case "remove" -> {
                count(args, 3, 3);
                service.removeBudget(args.get(2));
            }
            default -> throw new IllegalArgumentException("budget: set или remove");
        }
        changed();
    }

    private void add(List<String> args) {
        count(args, 3, Integer.MAX_VALUE);
        LocalDate date = LocalDate.now();
        int descriptionStart = 3;
        if (args.size() > 3 && args.get(3).matches("[+-]?\\d{4,}-.*")) {
            date = LocalDate.parse(args.get(3));
            descriptionStart = 4;
        }
        service.add(Type.parse(args.get(0)), args.get(1), args.get(2), date, join(args, descriptionStart));
        changed();
    }

    private String join(List<String> args, int start) {
        return String.join(" ", args.subList(start, args.size()));
    }

    private void changed() {
        output.println("Готово.");
        service.wallet().warnings().forEach(warning -> output.println("Предупреждение: " + warning));
    }

    private void report(List<String> args) throws IOException {
        Type type = null;
        Set<String> categories = new HashSet<>();
        Set<String> options = new HashSet<>();
        LocalDate from = null;
        LocalDate to = null;
        String file = null;
        for (int i = 1; i < args.size(); i++) {
            String token = args.get(i);
            int separator = token.indexOf('=');
            String option = separator < 0 ? token : token.substring(0, separator);
            if (!options.add(option) && !option.equals("--categories")) {
                throw new IllegalArgumentException("Повтор параметра: " + option);
            }
            String value;
            if (separator >= 0) {
                value = token.substring(separator + 1);
            } else if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
                throw new IllegalArgumentException("Отсутствует значение параметра: " + option);
            } else {
                value = args.get(++i);
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Пустое значение параметра: " + option);
            }
            switch (option) {
                case "--type" -> type = Type.parse(value);
                case "--from" -> from = LocalDate.parse(value);
                case "--to" -> to = LocalDate.parse(value);
                case "--file" -> file = value;
                case "--categories" -> {
                    categories.add(value);
                    while (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                        categories.add(args.get(++i));
                    }
                }
                default -> throw new IllegalArgumentException("Неизвестный параметр отчёта: " + option);
            }
        }
        Report report = service.wallet().report(new ReportFilter(type, categories, from, to));
        String text = formatter.report(report);
        if (file == null) {
            output.print(text);
        } else {
            outputFiles.write(file, text);
            output.println("Отчёт записан: " + file);
            report.warnings().forEach(warning -> output.println("Предупреждение: " + warning));
        }
    }

    private void count(List<String> args, int minimum, int maximum) {
        if (args.size() < minimum || args.size() > maximum) {
            throw new IllegalArgumentException("Неверное число аргументов. Синтаксис: help");
        }
    }

    private void help() {
        output.println("""
                register <login>                 Регистрация; пароль отдельной строкой
                login <login>                    Вход; пароль отдельной строкой
                logout                           Сохранить данные и выйти из аккаунта
                exit                             Сохранить данные и завершить программу
                category add <income|expense> <name>
                category rename <income|expense> <old> <new>
                category delete <income|expense> <name>
                category list
                budget set <expense-category> <amount>
                budget remove <expense-category>
                income <category> <amount> [yyyy-MM-dd] [description]
                expense <category> <amount> [yyyy-MM-dd] [description]
                transfer <login> <amount> [description]
                report [--type income|expense] [--categories <name1> <name2> ...]
                       [--from yyyy-MM-dd] [--to yyyy-MM-dd] [--file path]
                history
                export <path.csv>                Снимок своего кошелька
                import <path.csv>                Полная замена своего кошелька
                help
                Примеры:
                  category add expense "Коммунальные услуги"
                  budget set "Коммунальные услуги" 4000
                  expense "Коммунальные услуги" 4500 2026-01-15 "Счёт за квартиру"
                  report --categories Еда "Коммунальные услуги" --from 2026-01-01
                  report --categories=--Food --categories=--Taxi
                Параметры отчёта также принимают форму --option=value, включая имена с --.
                Суммы — рубли, точка и до двух знаков после неё. Бюджеты — за всю историю.
                Для кавычки внутри аргумента используйте \\"; для обратной косой черты — \\\\.
                """);
    }

    @FunctionalInterface
    interface PasswordInput {
        char[] read() throws IOException;
    }
}
