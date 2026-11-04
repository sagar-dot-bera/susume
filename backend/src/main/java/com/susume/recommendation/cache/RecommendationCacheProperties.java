package com.susume.recommendation.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Settings for the recommendation result cache ({@code recommendation.cache.*}). */
@Data
@ConfigurationProperties(prefix = "recommendation.cache")
public class RecommendationCacheProperties {

    /** Off: every request is computed, nothing is read from or written to Redis. */
    private boolean enabled = true;

    /**
     * Entry TTL by the strategy that served the response (the response's {@code strategy} field),
     * so degraded results are cached briefly and ML-ranked ones longer.
     */
    private Map<String, Duration> ttl = new HashMap<>(Map.of(
            "personalized_ml_reranked", Duration.ofMinutes(15),
            "personalized_cold_start_popularity", Duration.ofMinutes(5),
            // ML ranking was unavailable; retry it soon.
            "personalized", Duration.ofMinutes(1),
            "trending", Duration.ofMinutes(5)));

    /** TTL for strategies not listed in {@link #ttl}. */
    private Duration defaultTtl = Duration.ofMinutes(5);

    /** Each TTL is randomised by up to this fraction either way so entries written together don't expire together. */
    private double ttlJitter = 0.1;

    /**
     * How long a tenant or user generation token lives after its last bump. Must outlive every
     * entry TTL (checked at startup); interaction-service must use the same value.
     */
    private Duration generationTtl = Duration.ofHours(24);

    /** After a Redis error, skip the cache for this long instead of waiting on Redis timeouts per request. */
    private Duration failureBackoff = Duration.ofSeconds(30);

    public Duration ttlFor(String strategy) {
        return strategy == null ? defaultTtl : ttl.getOrDefault(strategy, defaultTtl);
    }

    public Duration maxTtl() {
        return ttl.values().stream().reduce(defaultTtl, (a, b) -> a.compareTo(b) >= 0 ? a : b);
    }
}
