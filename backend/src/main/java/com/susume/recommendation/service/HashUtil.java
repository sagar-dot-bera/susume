package com.susume.recommendation.service;

import java.security.MessageDigest;

public class HashUtil {

    public static String hash(String input) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error occurred while hashing the input", e);
        }
    }
}
