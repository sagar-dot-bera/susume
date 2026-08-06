package com.susume.recommendation.entity;

public enum InteractionType {
    VIEW(1),
    CLICK(2),
    LIKE(3),
    SHARE(4),
    CART(7),
    PURCHASE(10);

    private final int weight;

    InteractionType(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    /**
     * Parse interaction type from string, case-insensitive.
     */
    public static InteractionType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return InteractionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
