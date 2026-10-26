package com.susume.recommendation.service;

import com.susume.recommendation.config.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/**
 * Evicts every cached recommendation list for a user, whatever limit it was requested with.
 *
 * Entries are keyed "<tenantId>:<externalUserId>:<limit>" (see RecommendationService), which
 * {@code @CacheEvict} cannot match by prefix, so this deletes by pattern instead. The Python
 * interaction-service evicts the same Redis pattern.
 */
@Component
@Slf4j
public class RecommendationCacheEvictor {

    private final CacheManager cacheManager;

    public RecommendationCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public static String userKeyPrefix(UUID tenantId, String externalUserId) {
        return tenantId + ":" + externalUserId + ":";
    }

    public void evictUser(UUID tenantId, String externalUserId) {
        Cache cache = cacheManager.getCache(RedisConfig.RECOMMENDATION_CACHE);
        if (cache == null) {
            return;
        }
        String prefix = userKeyPrefix(tenantId, externalUserId);
        try {
            if (cache instanceof RedisCache redisCache) {
                String pattern = redisCache.getCacheConfiguration().getKeyPrefixFor(cache.getName())
                        + escapeGlob(prefix) + "*";
                redisCache.getNativeCache().clean(cache.getName(), pattern.getBytes(StandardCharsets.UTF_8));
            } else if (cache.getNativeCache() instanceof ConcurrentMap<?, ?> map) {
                map.keySet().removeIf(key -> key.toString().startsWith(prefix));
            } else {
                cache.clear();
            }
        } catch (RuntimeException e) {
            // Stale entries expire with the cache TTL; don't fail the write over it.
            log.warn("Failed to evict recommendations for {}", prefix, e);
        }
    }

    /** Escape Redis glob metacharacters so a user id can't widen the pattern. */
    static String escapeGlob(String value) {
        return value.replaceAll("([*?\\[\\]\\\\])", "\\\\$1");
    }
}
