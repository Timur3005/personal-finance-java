package ru.mephi.finance.infra;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import ru.mephi.finance.core.Passwords;

public final class Pbkdf2Passwords implements Passwords {
    private static final int ITERATIONS = 210000;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String hash(char[] password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        Base64.Encoder encoder = Base64.getEncoder();
        return "pbkdf2-sha256$" + ITERATIONS + "$" + encoder.encodeToString(salt) + "$"
                + encoder.encodeToString(derive(password, salt, ITERATIONS));
    }

    @Override
    public boolean verify(char[] password, String encoded) {
        if (password == null || password.length == 0 || password.length > 1024) {
            return false;
        }
        try {
            String[] fields = encoded.split("\\$", -1);
            if (fields.length != 4 || !fields[0].equals("pbkdf2-sha256")) {
                return false;
            }
            int iterations = Integer.parseInt(fields[1]);
            byte[] salt = Base64.getDecoder().decode(fields[2]);
            byte[] expected = Base64.getDecoder().decode(fields[3]);
            if (iterations < 100000 || iterations > 1000000 || salt.length != 16 || expected.length != 32) {
                return false;
            }
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 недоступен в этой JVM", e);
        } finally {
            specification.clearPassword();
        }
    }
}
