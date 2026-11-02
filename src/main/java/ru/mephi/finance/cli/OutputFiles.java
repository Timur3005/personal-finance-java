package ru.mephi.finance.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

final class OutputFiles {
    private final Path dataDirectory;

    OutputFiles(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    void write(String filename, String content) throws IOException {
        Path target = Path.of(filename).toAbsolutePath().normalize();
        if (target.getParent() == null || Files.isDirectory(target)) {
            throw new IllegalArgumentException("Укажите путь к файлу, а не каталогу");
        }
        Path ancestor = target;
        while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            ancestor = ancestor.getParent();
        }
        Path resolved = ancestor.toRealPath().resolve(ancestor.relativize(target)).normalize();
        for (String reserved : List.of("state.bin", "finance.lock")) {
            Path protectedPath = dataDirectory.resolve(reserved);
            if (resolved.equals(protectedPath)
                    || (Files.exists(target) && Files.exists(protectedPath) && Files.isSameFile(target, protectedPath))) {
                throw new IllegalArgumentException("Нельзя перезаписывать служебный файл: " + reserved);
            }
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
