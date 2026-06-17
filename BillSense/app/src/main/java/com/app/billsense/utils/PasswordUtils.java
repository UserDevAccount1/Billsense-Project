package com.app.billsense.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class PasswordUtils {

    private static final int SALT_LENGTH = 16;

    /**
     * Hashes a password using SHA-256 with a random salt.
     *
     * @param password the plaintext password to hash
     * @return a string in the format "salt:hash" (both hex-encoded)
     */
    public static String hashPassword(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hash = digest.digest(password.getBytes());

            return bytesToHex(salt) + ":" + bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies a plaintext password against a stored "salt:hash" string.
     *
     * @param input      the plaintext password to verify
     * @param storedHash the stored hash in "salt:hash" format
     * @return true if the password matches, false otherwise
     */
    public static boolean verifyPassword(String input, String storedHash) {
        if (input == null || storedHash == null || !isHashed(storedHash)) {
            return false;
        }
        try {
            String[] parts = storedHash.split(":");
            byte[] salt = hexToBytes(parts[0]);
            byte[] expectedHash = hexToBytes(parts[1]);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] actualHash = digest.digest(input.getBytes());

            // Constant-time comparison to prevent timing attacks
            if (actualHash.length != expectedHash.length) {
                return false;
            }
            int result = 0;
            for (int i = 0; i < actualHash.length; i++) {
                result |= actualHash[i] ^ expectedHash[i];
            }
            return result == 0;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Checks if a value is already in hashed format (contains a colon separator
     * with valid hex strings on both sides).
     *
     * @param value the value to check
     * @return true if the value appears to be a hashed password
     */
    public static boolean isHashed(String value) {
        if (value == null || !value.contains(":")) {
            return false;
        }
        String[] parts = value.split(":");
        if (parts.length != 2) {
            return false;
        }
        // Salt should be SALT_LENGTH bytes = SALT_LENGTH * 2 hex chars
        // Hash should be 32 bytes (SHA-256) = 64 hex chars
        return parts[0].length() == SALT_LENGTH * 2 && parts[1].length() == 64
                && parts[0].matches("[0-9a-f]+") && parts[1].matches("[0-9a-f]+");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
