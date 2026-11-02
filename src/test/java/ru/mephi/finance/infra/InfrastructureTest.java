package ru.mephi.finance.infra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mephi.finance.core.Entry;
import ru.mephi.finance.core.FinanceService;
import ru.mephi.finance.core.Repository;
import ru.mephi.finance.core.State;
import ru.mephi.finance.core.Type;
import ru.mephi.finance.core.Wallet;

class InfrastructureTest {
    @TempDir Path directory;

    @Test
    void passwordsAreSaltedAndVerifiedWithoutPlaintext() {
        Pbkdf2Passwords passwords = new Pbkdf2Passwords();
        String first = passwords.hash("секрет".toCharArray());
        assertNotEquals(first, passwords.hash("секрет".toCharArray()));
        assertFalse(first.contains("секрет"));
        assertTrue(passwords.verify("секрет".toCharArray(), first));
        assertFalse(passwords.verify("wrong".toCharArray(), first));
    }

    @Test
    void registrationLoginAndRestartPreserveUnicodeAndIsolateUsers() throws IOException {
        try (FileRepository repository = new FileRepository(directory)) {
            FinanceService service = new FinanceService(repository, new Pbkdf2Passwords());
            service.register("alice", "пароль".toCharArray());
            service.register("bob", "other".toCharArray());
            service.login("alice", "пароль".toCharArray());
            service.addCategory(Type.INCOME, "Зарплата 💰");
            service.add(Type.INCOME, "Зарплата 💰", "12.34", LocalDate.of(2026, 1, 1), "доход");
            service.logout();
            service.login("bob", "other".toCharArray());
            assertTrue(service.wallet().entries().isEmpty());
            service.logout();
        }
        try (FileRepository repository = new FileRepository(directory)) {
            FinanceService service = new FinanceService(repository, new Pbkdf2Passwords());
            service.login("alice", "пароль".toCharArray());
            assertEquals("12.34", service.wallet().balance().toPlainString());
            assertEquals("Зарплата 💰", service.wallet().categories().get(0).name());
        }
    }

    @Test
    void duplicateLoginWrongPasswordAndUnauthenticatedAccessAreRejected() throws IOException {
        try (FileRepository repository = new FileRepository(directory)) {
            FinanceService service = new FinanceService(repository, new Pbkdf2Passwords());
            service.register("alice", "secret".toCharArray());
            assertThrows(IllegalArgumentException.class,
                    () -> service.register("alice", "different".toCharArray()));
            assertThrows(IllegalArgumentException.class,
                    () -> service.login("alice", "wrong".toCharArray()));
            assertThrows(IllegalArgumentException.class, service::wallet);
        }
    }

    @Test
    void secondProcessCannotUseSameDirectoryUntilLockReleased() throws IOException {
        try (FileRepository first = new FileRepository(directory)) {
            assertTrue(first.load().users().isEmpty());
            assertThrows(IOException.class, () -> new FileRepository(directory));
        }
        try (FileRepository reopened = new FileRepository(directory)) {
            assertTrue(reopened.load().users().isEmpty());
        }
    }

    @Test
    void corruptedSnapshotIsReportedAndNeverReplacedWithEmptyState() throws IOException {
        Path snapshot = directory.resolve("state.bin");
        Files.writeString(snapshot, "broken snapshot");
        byte[] before = Files.readAllBytes(snapshot);
        try (FileRepository repository = new FileRepository(directory)) {
            assertThrows(IOException.class, () -> new FinanceService(repository, new Pbkdf2Passwords()));
        }
        assertArrayEquals(before, Files.readAllBytes(snapshot));
    }

    @Test
    void snapshotChecksumDetectsModification() throws IOException {
        try (FileRepository repository = new FileRepository(directory)) {
            new FinanceService(repository, new Pbkdf2Passwords()).register("alice", "secret".toCharArray());
        }
        Path snapshot = directory.resolve("state.bin");
        byte[] bytes = Files.readAllBytes(snapshot);
        bytes[bytes.length - 1] ^= 1;
        Files.write(snapshot, bytes);
        try (FileRepository repository = new FileRepository(directory)) {
            assertThrows(IOException.class, repository::load);
        }
    }

    @Test
    void transferHasLinkedEntriesAndBothWalletsSurviveRestart() throws IOException {
        try (FileRepository repository = new FileRepository(directory)) {
            FinanceService service = funded(repository);
            service.transfer("bob", "25", "подарок");
            Entry outgoing = service.wallet().entries().get(1);
            assertEquals("75.00", service.wallet().balance().toPlainString());
            assertEquals("bob", outgoing.counterparty());
            service.logout();
            service.login("bob", "other".toCharArray());
            Entry incoming = service.wallet().entries().get(0);
            assertEquals(outgoing.transferId(), incoming.transferId());
            assertEquals("alice", incoming.counterparty());
        }
        try (FileRepository repository = new FileRepository(directory)) {
            FinanceService service = new FinanceService(repository, new Pbkdf2Passwords());
            service.login("bob", "other".toCharArray());
            assertEquals("25.00", service.wallet().balance().toPlainString());
        }
    }

    @Test
    void invalidTransfersLeaveBothWalletsUntouched() throws IOException {
        try (FileRepository repository = new FileRepository(directory)) {
            FinanceService service = funded(repository);
            Wallet before = service.wallet();
            assertThrows(IllegalArgumentException.class, () -> service.transfer("alice", "1", ""));
            assertThrows(IllegalArgumentException.class, () -> service.transfer("missing", "1", ""));
            assertThrows(IllegalArgumentException.class, () -> service.transfer("bob", "101", ""));
            assertThrows(IllegalArgumentException.class, () -> service.transfer("bob", "0", ""));
            assertEquals(before, service.wallet());
            service.logout();
            service.login("bob", "other".toCharArray());
            assertTrue(service.wallet().entries().isEmpty());
        }
    }

    @Test
    void failedTransferSaveRollsBackAndFailedLogoutRetainsSession() throws IOException {
        FailingRepository repository = new FailingRepository();
        FinanceService service = funded(repository);
        Wallet before = service.wallet();
        repository.fail = true;
        assertThrows(IOException.class, () -> service.transfer("bob", "10", ""));
        assertEquals(before, service.wallet());
        assertThrows(IOException.class, service::logout);
        assertEquals("alice", service.currentLogin());
    }

    @Test
    void realFilesystemWriteFailureDoesNotRegisterUserAndCanBeRetried() throws IOException {
        try (FileRepository repository = new FileRepository(directory)) {
            FinanceService service = new FinanceService(repository, new Pbkdf2Passwords());
            Files.createDirectory(directory.resolve("state.bin"));
            assertThrows(IOException.class, () -> service.register("alice", "secret".toCharArray()));
            Files.delete(directory.resolve("state.bin"));
            service.register("alice", "secret".toCharArray());
            service.login("alice", "secret".toCharArray());
            assertEquals("alice", service.currentLogin());
            try (var paths = Files.list(directory)) {
                assertFalse(paths.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")));
            }
        }
    }

    private FinanceService funded(Repository repository) throws IOException {
        FinanceService service = new FinanceService(repository, new Pbkdf2Passwords());
        service.register("alice", "secret".toCharArray());
        service.register("bob", "other".toCharArray());
        service.login("alice", "secret".toCharArray());
        service.addCategory(Type.INCOME, "Зарплата");
        service.add(Type.INCOME, "Зарплата", "100", LocalDate.of(2026, 1, 1), "");
        return service;
    }

    private static class FailingRepository implements Repository {
        private State state = State.empty();
        private boolean fail;

        public State load() { return state; }

        public void save(State value) throws IOException {
            if (fail) { throw new IOException("disk unavailable"); }
            state = value;
        }
    }
}
