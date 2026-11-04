package com.susume.recommendation.cache;

import java.time.Duration;
import java.util.List;

/** The few key-value operations the recommendation cache needs; Redis in production, a map in tests. */
public interface RecommendationCacheStore {

    /** Values for the keys, in order, with {@code null} for missing keys. */
    List<String> multiGet(List<String> keys);

    void set(String key, String value, Duration ttl);
}
