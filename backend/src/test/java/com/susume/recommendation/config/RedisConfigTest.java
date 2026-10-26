package com.susume.recommendation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void recommendationCacheRoundTripsResponsesAsJson() {
        RedisCacheManager manager = new RedisConfig().cacheManager(mock(RedisConnectionFactory.class), new ObjectMapper());
        manager.afterPropertiesSet();
        RedisCacheConfiguration config = manager.getCacheConfigurations().get(RedisConfig.RECOMMENDATION_CACHE);

        RecommendationResponse original = new RecommendationResponse(
                UUID.randomUUID(),
                List.of(new RecommendationItemResponse("sku-1", Map.of("title", "Shoes", "price", 49.5), 0.87)),
                "personalized");

        ByteBuffer bytes = config.getValueSerializationPair().write(original);
        Object restored = config.getValueSerializationPair().read(bytes);

        assertEquals(original, restored);
    }
}
