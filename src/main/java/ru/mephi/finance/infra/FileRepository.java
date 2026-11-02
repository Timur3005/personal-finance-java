package ru.mephi.finance.infra;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import ru.mephi.finance.core.Repository;
import ru.mephi.finance.core.State;
import ru.mephi.finance.core.User;

public final class FileRepository implements Repository {
    private static final int MAGIC = 0x46494e31;
    private static final int MAX_SIZE = 50 * 1024 * 1024;
    private final Path directory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final CsvCodec csv = new CsvCodec();

    public FileRepository(Path directory) throws IOException {
        Files.createDirectories(directory);
        this.directory = directory.toRealPath();
        lockChannel = FileChannel.open(this.directory.resolve("finance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock acquired = lockChannel.tryLock();
            if (acquired == null) {
                throw new IOException("Каталог данных уже используется другим процессом");
            }
            lock = acquired;
        } catch (IOException | OverlappingFileLockException e) {
            lockChannel.close();
            throw new IOException("Каталог данных уже используется или недоступен: " + directory, e);
        }
    }

    public Path directory() {
        return directory;
    }

    @Override
    public State load() throws IOException {
        Path snapshot = directory.resolve("state.bin");
        if (!Files.exists(snapshot)) {
            return State.empty();
        }
        if (Files.size(snapshot) > MAX_SIZE) {
            throw new IOException("Снимок превышает допустимый размер 50 МиБ");
        }
        byte[] data = Files.readAllBytes(snapshot);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Неверный формат снимка");
            }
            byte[] digest = input.readNBytes(32);
            byte[] payload = input.readAllBytes();
            if (!MessageDigest.isEqual(digest, digest(payload))) {
                throw new IOException("Нарушена целостность снимка");
            }
            return decode(payload);
        } catch (IllegalArgumentException e) {
            throw new IOException("Повреждён снимок данных: " + e.getMessage(), e);
        }
    }

    private State decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = input.readInt();
            if (count < 0 || count > 100000) {
                throw new IOException("Неверное число пользователей в снимке");
            }
            Map<String, User> users = new HashMap<>();
            for (int i = 0; i < count; i++) {
                String login = readText(input);
                String password = readText(input);
                User user = new User(login, password, csv.decode(readText(input)));
                if (users.put(login, user) != null) {
                    throw new IOException("Повтор пользователя в снимке");
                }
            }
            if (input.available() != 0) {
                throw new IOException("Лишние данные в снимке");
            }
            return new State(users);
        }
    }

    private String readText(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available()) {
            throw new IOException("Неверная длина поля снимка");
        }
        byte[] bytes = input.readNBytes(length);
        return java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    }

    private void writeText(DataOutputStream output, String text) throws IOException {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    @Override
    public void save(State state) throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(payloadBytes)) {
            output.writeInt(state.users().size());
            for (User user : state.users().values().stream().sorted(java.util.Comparator.comparing(User::login)).toList()) {
                writeText(output, user.login());
                writeText(output, user.passwordHash());
                writeText(output, csv.encode(user.wallet()));
            }
        }
        byte[] payload = payloadBytes.toByteArray();
        if (payload.length + 36 > MAX_SIZE) {
            throw new IOException("Данные превышают допустимый размер снимка 50 МиБ");
        }
        ByteArrayOutputStream snapshotBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(snapshotBytes)) {
            output.writeInt(MAGIC);
            output.write(digest(payload));
            output.write(payload);
        }
        Path temporary = Files.createTempFile(directory, "state-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(snapshotBytes.toByteArray());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, directory.resolve("state.bin"), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private byte[] digest(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен в JVM", e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            lockChannel.close();
        }
    }
}
