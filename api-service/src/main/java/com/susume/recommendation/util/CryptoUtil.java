package com.susume.recommendation.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public class CryptoUtil {

    private static final int API_KEY_LENGTH = 32;
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a cryptographically secure 32-character hex API key.
     */
    public static String generateAPIKey() {
        byte[] randomBytes = new byte[16]; // 16 bytes = 32 hex chars
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    /**
     * SHA-256 hash the API key for storage.
     */
    public static String hashAPIKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    public static boolean constantTimeEquals(String provided, String stored) {
        if (provided == null || stored == null) {
            return false;
        }

        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        byte[] storedBytes = stored.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(providedBytes, storedBytes);
    }
}
