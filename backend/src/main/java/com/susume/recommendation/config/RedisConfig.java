package com.susume.recommendation.config;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.susume.recommendation.dto.RecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
@Slf4j
public class RedisConfig implements CachingConfigurer {

    public static final String RECOMMENDATION_CACHE = "recommendation";

    @Bean
    RedisCacheManager cacheManager(
            RedisConnectionFactory factory, ObjectMapper objectMapper) {

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15));

        // Store recommendations as typed JSON: the DTOs aren't java.io.Serializable,
        // so the default JDK serializer would fail on the first cache write.
        RedisCacheConfiguration recommendationConfig = config.serializeValuesWith(SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(objectMapper, RecommendationResponse.class)));

        // SCAN rather than KEYS for pattern evictions (RecommendationCacheEvictor), so they don't block Redis.
        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(factory, BatchStrategies.scan(1000));

        return RedisCacheManager.builder(writer)
                .cacheDefaults(config)
                .withCacheConfiguration(RECOMMENDATION_CACHE, recommendationConfig)
                .build();
    }

    /**
     * Treat Redis as an optimisation: if it is unreachable or holds an unreadable
     * entry, log it and fall through to computing the result instead of failing the request.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed for {}::{}", cache.getName(), key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed for {}::{}", cache.getName(), key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed for {}::{}", cache.getName(), key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed for {}", cache.getName(), e);
            }
        };
    }
}
