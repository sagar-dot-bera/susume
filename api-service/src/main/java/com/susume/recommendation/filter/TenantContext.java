package com.susume.recommendation.filter;

import java.util.UUID;

/**
 * ThreadLocal context holder for tenant ID.
 */
public class TenantContext {
    private static final ThreadLocal<UUID> tenantIdHolder = new ThreadLocal<>();

    public static void setTenantId(UUID tenantId) {
        tenantIdHolder.set(tenantId);
    }

    public static UUID getTenantId() {
        return tenantIdHolder.get();
    }

    public static void clear() {
        tenantIdHolder.remove();
    }
}
