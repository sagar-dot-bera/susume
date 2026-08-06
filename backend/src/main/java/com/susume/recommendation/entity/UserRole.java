package com.susume.recommendation.entity;

/**
 * UserRole
 */
public enum UserRole {
    ADMIN,
    MEMBER,
    GUEST;

    public static UserRole fromString(String role) {
        for (UserRole userRole : UserRole.values()) {
            if (userRole.name().equalsIgnoreCase(role)) {
                return userRole;
            }
        }
        throw new IllegalArgumentException("Invalid role: " + role);
    }
}
