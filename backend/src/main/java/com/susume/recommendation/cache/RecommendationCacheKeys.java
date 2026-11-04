package com.susume.recommendation.cache;

import java.util.UUID;

/**
 * Redis key layout for cached recommendations. Every key starts with the tenant id, taken from
 * the authenticated API key, so one tenant's requests can never read another tenant's entries.
 * The tenant is wrapped in a hash tag ({@code {...}}) so a tenant's keys share a Redis Cluster
 * slot and can be fetched with one MGET.
 *
 * <pre>
 * reco:{tenant}:gen                                  tenant generation (catalog changed)
 * reco:{tenant}:user:{user}:gen                      user generation (user interacted)
 * reco:{tenant}:personalized:user:{user}:limit:{n}:model:{version}:gen:{tenantGen}.{userGen}
 * reco:{tenant}:trending:limit:{n}:days:{d}:gen:{tenantGen}
 * </pre>
 *
 * Invalidation never deletes entries: it replaces a generation token, which changes the key
 * every later read looks up, and orphaned entries expire on their TTL. interaction-service
 * writes the user generation key too (app/cache.py), so its format must stay in sync.
 */
public final class RecommendationCacheKeys {

    /** Generation used while no generation key exists (nothing has been invalidated recently). */
    public static final String INITIAL_GENERATION = "0";

    private RecommendationCacheKeys() {
    }

    public static String tenantGeneration(UUID tenantId) {
        return tenantPrefix(tenantId) + "gen";
    }

    public static String userGeneration(UUID tenantId, String externalUserId) {
        return tenantPrefix(tenantId) + "user:" + externalUserId + ":gen";
    }

    public static String personalized(UUID tenantId, UUID externalUserId, int limit, String modelVersion,
            String tenantGeneration, String userGeneration) {
        return tenantPrefix(tenantId) + "personalized:user:" + externalUserId + ":limit:" + limit
                + ":model:" + modelVersion + ":gen:" + tenantGeneration + "." + userGeneration;
    }

    public static String trending(UUID tenantId, int limit, int days, String tenantGeneration) {
        return tenantPrefix(tenantId) + "trending:limit:" + limit + ":days:" + days + ":gen:" + tenantGeneration;
    }

    private static String tenantPrefix(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for recommendation cache keys");
        }
        return "reco:{" + tenantId + "}:";
    }
}
