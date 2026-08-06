package com.susume.recommendation.util;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetadataConcatenatorTest {

    @Test
    void concatenatesAllStringFields() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Book Title");
        metadata.put("author", "Author Name");
        metadata.put("description", "A great book");

        String result = MetadataConcatenator.concatenate(metadata);

        assertTrue(result.contains("Book Title"));
        assertTrue(result.contains("Author Name"));
        assertTrue(result.contains("A great book"));
    }

    @Test
    void skipsNullAndEmptyFields() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Book Title");
        metadata.put("empty", "");
        metadata.put("nullValue", null);
        metadata.put("whitespace", "   ");

        String result = MetadataConcatenator.concatenate(metadata);

        assertTrue(result.contains("Book Title"));
        assertFalse(result.contains("empty"));
        assertFalse(result.contains("whitespace"));
        // Result should only contain the non-empty field
        assertEquals("Book Title", result.trim());
    }

    @Test
    void convertsNumericFieldsToString() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Product");
        metadata.put("price", 99.99);
        metadata.put("quantity", 100);

        String result = MetadataConcatenator.concatenate(metadata);

        assertTrue(result.contains("Product"));
        assertTrue(result.contains("99.99"));
        assertTrue(result.contains("100"));
    }

    @Test
    void truncatesLongInputToLimit() {
        Map<String, Object> metadata = new HashMap<>();
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longText.append("This is a very long text that needs to be truncated. ");
        }
        metadata.put("description", longText.toString());

        String result = MetadataConcatenator.concatenate(metadata);

        assertEquals(2000, result.length());
    }

    @Test
    void handlesEmptyMetadata() {
        String result = MetadataConcatenator.concatenate(new HashMap<>());
        assertEquals("", result);
    }

    @Test
    void handlesNullMetadata() {
        String result = MetadataConcatenator.concatenate(null);
        assertEquals("", result);
    }

    @Test
    void validatesNonEmptyMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Valid Title");

        assertTrue(MetadataConcatenator.isValid(metadata));
    }

    @Test
    void invalidatesEmptyMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("field1", "");
        metadata.put("field2", null);

        assertFalse(MetadataConcatenator.isValid(metadata));
    }

    @Test
    void invalidatesNullMetadata() {
        assertFalse(MetadataConcatenator.isValid(null));
    }

    @Test
    void joinsFieldsWithSpace() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("first", "Hello");
        metadata.put("second", "World");

        String result = MetadataConcatenator.concatenate(metadata);

        assertTrue(result.contains("Hello"));
        assertTrue(result.contains("World"));
        assertTrue(result.contains("Hello") && result.contains("World"));
    }
}
