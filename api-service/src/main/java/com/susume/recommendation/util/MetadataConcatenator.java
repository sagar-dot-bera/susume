package com.susume.recommendation.util;

import java.util.Map;

/**
 * Utility to concatenate metadata into a single text string for embedding.
 */
public class MetadataConcatenator {

    private static final int MAX_CHARS = 2000; // Safe estimate for 512 tokens

    /**
     * Concatenate metadata values into a single string.
     * Iterates over all fields, converts non-strings to string, skips null/empty
     * fields.
     * Joins with single space and truncates to MAX_CHARS.
     */
    public static String concatenate(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            String strValue = value.toString().trim();

            if (strValue.isEmpty()) {
                continue;
            }

            if (sb.length() > 0) {
                sb.append(" ");
            }

            sb.append(strValue);
        }

        // Truncate to MAX_CHARS
        if (sb.length() > MAX_CHARS) {
            return sb.substring(0, MAX_CHARS);
        }

        return sb.toString();
    }

    /**
     * Validate that metadata contains at least one non-empty string field.
     */
    public static boolean isValid(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }

        for (Object value : metadata.values()) {
            if (value != null) {
                String strValue = value.toString().trim();
                if (!strValue.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }
}
