package ru.mephi.finance.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import ru.mephi.finance.core.FinanceService;
import ru.mephi.finance.infra.FileRepository;
import ru.mephi.finance.infra.Pbkdf2Passwords;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        int code = run(args, input, output, System.console() == null ? null : () -> System.console().readPassword());
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args, Reader reader, PrintWriter output) {
        return run(args, new BufferedReader(reader), output, null);
    }

    private static int run(String[] args, BufferedReader input, PrintWriter output, Cli.PasswordInput passwordInput) {
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            output.println("Запуск: java -jar finance.jar [--data-dir <каталог>]\nПо умолчанию: data/");
            return 0;
        }
        if (args.length != 0 && (args.length != 2 || !args[0].equals("--data-dir"))) {
            output.println("Ошибка: запуск java -jar finance.jar [--data-dir <каталог>]");
            return 1;
        }
        try (FileRepository repository = new FileRepository(Path.of(args.length == 0 ? "data" : args[1]))) {
            FinanceService service = new FinanceService(repository, new Pbkdf2Passwords());
            Cli.PasswordInput passwords = passwordInput == null ? () -> {
                String line = input.readLine();
                return line == null ? null : line.toCharArray();
            } : passwordInput;
            return new Cli(service, input, output, passwords, repository.directory()).run();
        } catch (IOException | IllegalArgumentException e) {
            output.println("Ошибка: " + e.getMessage());
            return 1;
        } finally {
            output.flush();
        }
    }
}
