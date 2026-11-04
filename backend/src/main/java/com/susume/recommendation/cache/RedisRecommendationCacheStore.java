package com.susume.recommendation.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisRecommendationCacheStore implements RecommendationCacheStore {

    private final StringRedisTemplate redis;

    public RedisRecommendationCacheStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public List<String> multiGet(List<String> keys) {
        List<String> values = redis.opsForValue().multiGet(keys);
        if (values == null) {
            // Only returned inside a pipeline/transaction, which we never open.
            throw new IllegalStateException("Redis MGET returned no reply");
        }
        return values;
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }
}
