package com.susume.recommendation.filter;

import java.util.UUID;

import com.susume.recommendation.entity.UserRole;

/**
 * ThreadLocal context holder for JWT claims.
 */
public class JwtContext {
    private static final ThreadLocal<UUID> userIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<UUID> tenantIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<UserRole> roleHolder = new ThreadLocal<>();

    public static void setUserId(UUID userId) {
        userIdHolder.set(userId);
    }

    public static UUID getUserId() {
        return userIdHolder.get();
    }

    public static void setTenantId(UUID tenantId) {
        tenantIdHolder.set(tenantId);
    }

    public static UUID getTenantId() {
        return tenantIdHolder.get();
    }

    public static void setRole(UserRole role) {
        roleHolder.set(role);
    }

    public static UserRole getRole() {
        return roleHolder.get();
    }

    public static void clear() {
        userIdHolder.remove();
        tenantIdHolder.remove();
        roleHolder.remove();
    }
}
