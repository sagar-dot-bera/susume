package com.susume.recommendation.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationCacheTest {

    /** Map-backed store that records TTLs and can be switched to fail like an unreachable Redis. */
    static class FakeStore implements RecommendationCacheStore {
        final Map<String, String> values = new ConcurrentHashMap<>();
        final Map<String, Duration> ttls = new ConcurrentHashMap<>();
        volatile boolean failing;
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<String> multiGet(List<String> keys) {
            calls.incrementAndGet();
            if (failing) {
                throw new IllegalStateException("redis down");
            }
            List<String> out = new ArrayList<>();
            keys.forEach(k -> out.add(values.get(k)));
            return out;
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            calls.incrementAndGet();
            if (failing) {
                throw new IllegalStateException("redis down");
            }
            values.put(key, value);
            ttls.put(key, ttl);
        }

        List<String> entryKeys() {
            return values.keySet().stream().filter(k -> !k.endsWith(":gen")).toList();
        }
    }

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private FakeStore store;
    private RecommendationCacheProperties properties;
    private SimpleMeterRegistry meters;
    private RecommendationCache cache;
    private AtomicInteger computations;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        properties = new RecommendationCacheProperties();
        meters = new SimpleMeterRegistry();
        cache = new RecommendationCache(store, properties, new ObjectMapper(), meters);
        computations = new AtomicInteger();
    }

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Supplier<RecommendationResponse> compute(String strategy) {
        return () -> {
            computations.incrementAndGet();
            return new RecommendationResponse(user,
                    List.of(new RecommendationItemResponse("sku-1", Map.of("title", "Shoes", "price", 49.5), 0.87)),
                    strategy, "v2");
        };
    }

    private RecommendationResponse personalized(UUID tenantId, UUID userId, int limit, String model) {
        return cache.getPersonalized(tenantId, userId, limit, model, compute("personalized_ml_reranked"));
    }

    private double requests(String result) {
        var counter = meters.find("recommendation.cache.requests").tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void secondRequestIsServedFromCacheWithIdenticalContent() {
        RecommendationResponse first = personalized(tenant, user, 10, "v2");
        RecommendationResponse second = personalized(tenant, user, 10, "v2");

        assertEquals(1, computations.get());
        assertEquals(first, second);
        assertEquals(1, requests("miss"));
        assertEquals(1, requests("hit"));
    }

    @Test
    void keysAreScopedByTenantUserLimitAndModel() {
        personalized(tenant, user, 10, "v2");
        personalized(UUID.randomUUID(), user, 10, "v2");
        personalized(tenant, UUID.randomUUID(), 10, "v2");
        personalized(tenant, user, 20, "v2");
        personalized(tenant, user, 10, "v3");

        assertEquals(5, computations.get());
        assertTrue(store.entryKeys().stream().allMatch(k -> k.startsWith("reco:{")));
    }

    @Test
    void personalizedAndTrendingDoNotShareEntries() {
        personalized(tenant, user, 10, "v2");
        cache.getTrending(tenant, 10, 30, compute("trending"));
        cache.getTrending(tenant, 10, 7, compute("trending"));
        cache.getTrending(tenant, 10, 30, compute("trending"));

        assertEquals(3, computations.get());
    }

    @Test
    void userInvalidationOnlyAffectsThatUser() {
        UUID other = UUID.randomUUID();
        personalized(tenant, user, 5, "v2");
        personalized(tenant, user, 20, "v2");
        personalized(tenant, other, 5, "v2");
        cache.getTrending(tenant, 10, 30, compute("trending"));

        cache.invalidateUser(tenant, user.toString());

        personalized(tenant, user, 5, "v2");
        personalized(tenant, user, 20, "v2");
        assertEquals(6, computations.get());
        personalized(tenant, other, 5, "v2");
        cache.getTrending(tenant, 10, 30, compute("trending"));
        assertEquals(6, computations.get());
    }

    @Test
    void tenantInvalidationAffectsEveryEntryOfThatTenantOnly() {
        UUID otherTenant = UUID.randomUUID();
        personalized(tenant, user, 10, "v2");
        cache.getTrending(tenant, 10, 30, compute("trending"));
        personalized(otherTenant, user, 10, "v2");

        cache.invalidateTenant(tenant);

        personalized(tenant, user, 10, "v2");
        cache.getTrending(tenant, 10, 30, compute("trending"));
        personalized(otherTenant, user, 10, "v2");
        assertEquals(5, computations.get());
    }

    @Test
    void invalidationWaitsForTheTransactionToCommit() {
        personalized(tenant, user, 10, "v2");
        TransactionSynchronizationManager.initSynchronization();

        cache.invalidateUser(tenant, user.toString());
        // Not committed yet: a concurrent reader still sees the old entry.
        personalized(tenant, user, 10, "v2");
        assertEquals(1, computations.get());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        personalized(tenant, user, 10, "v2");
        assertEquals(2, computations.get());
    }

    @Test
    void ttlDependsOnServedStrategyAndIsJittered() {
        properties.setTtlJitter(0.1);
        cache.getPersonalized(tenant, user, 10, "v2", compute("personalized_ml_reranked"));
        cache.getPersonalized(tenant, user, 11, "v2", compute("personalized"));
        cache.getTrending(tenant, 10, 30, compute("something_unlisted"));

        Map<String, Duration> ttls = new java.util.HashMap<>();
        store.ttls.forEach((k, v) -> {
            if (!k.endsWith(":gen")) {
                ttls.put(k.contains(":limit:10:model") ? "ml" : k.contains(":limit:11:") ? "fallback" : "other", v);
            }
        });
        assertBetween(ttls.get("ml"), Duration.ofMinutes(15), 0.1);
        assertBetween(ttls.get("fallback"), Duration.ofMinutes(1), 0.1);
        assertBetween(ttls.get("other"), properties.getDefaultTtl(), 0.1);

        long distinct = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> cache.jitteredTtl("personalized_ml_reranked")).distinct().count();
        assertTrue(distinct > 1, "TTLs should vary");
    }

    private static void assertBetween(Duration actual, Duration base, double jitter) {
        assertNotNull(actual);
        assertTrue(actual.toMillis() >= base.toMillis() * (1 - jitter) && actual.toMillis() <= base.toMillis() * (1 + jitter),
                actual + " outside " + base + " ± " + jitter);
    }

    @Test
    void concurrentMissesForTheSameKeyComputeOnce() throws Exception {
        int callers = 8;
        CountDownLatch allWaiting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<RecommendationResponse> slow = () -> {
            allWaiting.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return compute("personalized_ml_reranked").get();
        };

        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<RecommendationResponse>> results = new ArrayList<>();
            results.add(pool.submit(() -> cache.getPersonalized(tenant, user, 10, "v2", slow)));
            assertTrue(allWaiting.await(5, TimeUnit.SECONDS));
            for (int i = 1; i < callers; i++) {
                results.add(pool.submit(() -> cache.getPersonalized(tenant, user, 10, "v2", slow)));
            }
            // Let the followers reach the in-flight computation before it finishes.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (coalesced() < callers - 1 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            release.countDown();
            for (Future<RecommendationResponse> f : results) {
                assertNotNull(f.get(5, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, computations.get());
        assertEquals(callers - 1, coalesced());
    }

    private double coalesced() {
        var counter = meters.find("recommendation.cache.coalesced").counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void computationErrorsPropagateAndAreNotCached() {
        Supplier<RecommendationResponse> failing = () -> {
            computations.incrementAndGet();
            throw new IllegalStateException("db down");
        };

        assertThrows(IllegalStateException.class, () -> cache.getPersonalized(tenant, user, 10, "v2", failing));
        assertTrue(store.entryKeys().isEmpty());
        personalized(tenant, user, 10, "v2");
        assertEquals(2, computations.get());
    }

    @Test
    void redisFailureFallsBackToComputingAndBacksOff() {
        store.failing = true;

        RecommendationResponse response = personalized(tenant, user, 10, "v2");
        assertNotNull(response);
        int callsAfterFailure = store.calls.get();

        personalized(tenant, user, 10, "v2");
        personalized(tenant, user, 10, "v2");

        assertEquals(3, computations.get());
        assertEquals(callsAfterFailure, store.calls.get(), "Redis should not be retried during the backoff");
        assertEquals(3, requests("bypass"));
        assertEquals(1, meters.find("recommendation.cache.errors").tag("operation", "read").counter().count());
    }

    @Test
    void redisIsRetriedAfterTheBackoff() {
        properties.setFailureBackoff(Duration.ZERO);
        store.failing = true;
        personalized(tenant, user, 10, "v2");

        store.failing = false;
        personalized(tenant, user, 10, "v2");
        personalized(tenant, user, 10, "v2");

        assertEquals(2, computations.get());
        assertEquals(1, requests("hit"));
    }

    @Test
    void failedInvalidationDoesNotThrow() {
        store.failing = true;
        assertDoesNotThrow(() -> cache.invalidateUser(tenant, user.toString()));
        assertDoesNotThrow(() -> cache.invalidateTenant(tenant));
    }

    @Test
    void unreadableEntryIsRecomputedAndOverwritten() {
        personalized(tenant, user, 10, "v2");
        String key = store.entryKeys().get(0);
        store.values.put(key, "{not json");

        RecommendationResponse response = personalized(tenant, user, 10, "v2");

        assertEquals("sku-1", response.getRecommendations().get(0).getExternalItemId());
        assertEquals(2, computations.get());
        personalized(tenant, user, 10, "v2");
        assertEquals(2, computations.get());
    }

    @Test
    void disabledCacheAlwaysComputesAndNeverTouchesRedis() {
        properties.setEnabled(false);
        personalized(tenant, user, 10, "v2");
        personalized(tenant, user, 10, "v2");
        cache.invalidateUser(tenant, user.toString());

        assertEquals(2, computations.get());
        assertEquals(0, store.calls.get());
    }

    @Test
    void publishesLatencyPercentiles() {
        personalized(tenant, user, 10, "v2");
        personalized(tenant, user, 10, "v2");

        assertNotNull(meters.find("recommendation.cache.latency.percentile").tag("phi", "0.95").gauge());
        assertNotNull(meters.find("recommendation.compute.percentile").tag("phi", "0.5").gauge());
        assertEquals(1, meters.find("recommendation.compute").timer().count());
    }

    @Test
    void rejectsGenerationTtlShorterThanEntries() {
        properties.setGenerationTtl(Duration.ofMinutes(10));
        assertThrows(IllegalStateException.class, cache::validate);
    }

    @Test
    void keyFormatMatchesInteractionService() {
        // interaction-service (app/cache.py) writes this key; keep the two in sync.
        assertEquals("reco:{" + tenant + "}:user:user-1:gen", RecommendationCacheKeys.userGeneration(tenant, "user-1"));
        assertThrows(IllegalArgumentException.class, () -> RecommendationCacheKeys.tenantGeneration(null));
    }
}
