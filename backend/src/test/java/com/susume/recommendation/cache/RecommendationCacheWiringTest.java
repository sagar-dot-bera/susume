package com.susume.recommendation.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RecommendationCacheWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RecommendationCacheStore.class, () -> mock(RecommendationCacheStore.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withUserConfiguration(RecommendationCache.class);

    @Test
    void bindsPropertiesInTheApplicationYmlFormat() {
        runner.withPropertyValues(
                        "recommendation.cache.ttl.personalized_ml_reranked=20m",
                        "recommendation.cache.ttl-jitter=0.2",
                        "recommendation.cache.generation-ttl=12h",
                        "recommendation.cache.failure-backoff=10s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RecommendationCacheProperties properties = context.getBean(RecommendationCacheProperties.class);
                    assertThat(properties.ttlFor("personalized_ml_reranked")).isEqualTo(Duration.ofMinutes(20));
                    // Defaults for strategies the config doesn't mention are kept.
                    assertThat(properties.ttlFor("trending")).isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.getTtlJitter()).isEqualTo(0.2);
                    assertThat(properties.getFailureBackoff()).isEqualTo(Duration.ofSeconds(10));
                });
    }

    @Test
    void refusesToStartWhenGenerationsWouldExpireBeforeEntries() {
        runner.withPropertyValues("recommendation.cache.generation-ttl=10m")
                .run(context -> assertThat(context).hasFailed());
    }
}
