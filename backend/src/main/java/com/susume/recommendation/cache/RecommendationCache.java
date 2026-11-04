package com.susume.recommendation.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.susume.recommendation.dto.RecommendationResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Cache-aside store for final recommendation responses.
 *
 * <ul>
 *   <li><b>Keys</b> are tenant-scoped and include the user, limit, model version and the current
 *       tenant/user generation tokens (see {@link RecommendationCacheKeys}).</li>
 *   <li><b>Invalidation</b> replaces a generation token after the writing transaction commits,
 *       so a request that was computing during the write can't repopulate a key readers still use.</li>
 *   <li><b>Stampedes</b>: concurrent misses for the same key in this instance share one
 *       computation, and TTLs are jittered.</li>
 *   <li><b>Failures</b>: Redis is an optimisation. If it errors, the request is computed (still
 *       coalesced, so the database isn't hit once per request) and Redis is skipped for
 *       {@code failure-backoff} rather than paying its timeout on every request.</li>
 * </ul>
 *
 * Callers authorise the request and resolve the tenant before calling; the cache never decides
 * who may see what, it only keys by the tenant it is given.
 */
@Slf4j
@Service
@EnableConfigurationProperties(RecommendationCacheProperties.class)
public class RecommendationCache {

    static final String PERSONALIZED = "personalized";
    static final String TRENDING = "trending";

    private final RecommendationCacheStore store;
    private final RecommendationCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, CompletableFuture<RecommendationResponse>> inFlight = new ConcurrentHashMap<>();
    private volatile long redisRetryAtNanos;
    private volatile boolean redisBackingOff;

    public RecommendationCache(RecommendationCacheStore store, RecommendationCacheProperties properties,
            ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.store = store;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void validate() {
        Duration longestEntry = Duration.ofMillis(Math.round(properties.maxTtl().toMillis() * (1 + properties.getTtlJitter())));
        if (properties.getGenerationTtl().compareTo(longestEntry) <= 0) {
            // If a generation key expired while entries written under it were still live, readers
            // would fall back to the initial generation and miss any invalidation in between.
            throw new IllegalStateException("recommendation.cache.generation-ttl (" + properties.getGenerationTtl()
                    + ") must be longer than the longest entry TTL with jitter (" + longestEntry + ")");
        }
    }

    public RecommendationResponse getPersonalized(UUID tenantId, UUID externalUserId, int limit, String modelVersion,
            Supplier<RecommendationResponse> compute) {
        List<String> generationKeys = List.of(
                RecommendationCacheKeys.tenantGeneration(tenantId),
                RecommendationCacheKeys.userGeneration(tenantId, externalUserId.toString()));
        return getOrCompute(PERSONALIZED, generationKeys,
                gens -> RecommendationCacheKeys.personalized(tenantId, externalUserId, limit, modelVersion,
                        gens.get(0), gens.get(1)),
                compute);
    }

    public RecommendationResponse getTrending(UUID tenantId, int limit, int days, Supplier<RecommendationResponse> compute) {
        return getOrCompute(TRENDING, List.of(RecommendationCacheKeys.tenantGeneration(tenantId)),
                gens -> RecommendationCacheKeys.trending(tenantId, limit, days, gens.get(0)),
                compute);
    }

    /** The user's behaviour changed: drop their cached personalized lists. */
    public void invalidateUser(UUID tenantId, String externalUserId) {
        bumpGenerationAfterCommit("user", RecommendationCacheKeys.userGeneration(tenantId, externalUserId));
    }

    /** The tenant's catalog changed: drop every cached list for the tenant. */
    public void invalidateTenant(UUID tenantId) {
        bumpGenerationAfterCommit("tenant", RecommendationCacheKeys.tenantGeneration(tenantId));
    }

    private RecommendationResponse getOrCompute(String endpoint, List<String> generationKeys,
            Function<List<String>, String> entryKey, Supplier<RecommendationResponse> compute) {
        long start = System.nanoTime();
        if (!properties.isEnabled() || isBackingOff()) {
            RecommendationResponse response = coalesce(endpoint, bypassKey(generationKeys, entryKey), compute, false);
            record(endpoint, "bypass", start);
            return response;
        }

        String key;
        try {
            List<String> generations = store.multiGet(generationKeys).stream()
                    .map(g -> g != null ? g : RecommendationCacheKeys.INITIAL_GENERATION)
                    .toList();
            key = entryKey.apply(generations);
            RecommendationResponse cached = deserialize(key, store.multiGet(List.of(key)).get(0));
            if (cached != null) {
                record(endpoint, "hit", start);
                return cached;
            }
        } catch (RuntimeException e) {
            redisFailed("read", e);
            RecommendationResponse response = coalesce(endpoint, bypassKey(generationKeys, entryKey), compute, false);
            record(endpoint, "bypass", start);
            return response;
        }

        RecommendationResponse response = coalesce(endpoint, key, compute, true);
        record(endpoint, "miss", start);
        return response;
    }

    /**
     * Without Redis the generations are unknown, so requests coalesce on the initial-generation key:
     * at worst one joins a computation that started just before an invalidation.
     */
    private static String bypassKey(List<String> generationKeys, Function<List<String>, String> entryKey) {
        return entryKey.apply(generationKeys.stream().map(k -> RecommendationCacheKeys.INITIAL_GENERATION).toList());
    }

    /**
     * Runs {@code compute} once per key at a time in this instance; concurrent callers for the same
     * key wait for and share that result (or its exception).
     */
    private RecommendationResponse coalesce(String endpoint, String key, Supplier<RecommendationResponse> compute,
            boolean storeResult) {
        CompletableFuture<RecommendationResponse> mine = new CompletableFuture<>();
        CompletableFuture<RecommendationResponse> leader = inFlight.putIfAbsent(key, mine);
        if (leader != null) {
            counter("recommendation.cache.coalesced", "endpoint", endpoint).increment();
            try {
                return leader.join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException re ? re : e;
            }
        }

        try {
            RecommendationResponse response = timer("recommendation.compute", "endpoint", endpoint).record(compute);
            if (storeResult && response != null) {
                write(key, response);
            }
            mine.complete(response);
            return response;
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private void write(String key, RecommendationResponse response) {
        try {
            store.set(key, objectMapper.writeValueAsString(response), jitteredTtl(response.getStrategy()));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise recommendations for cache key {}", key, e);
        } catch (RuntimeException e) {
            redisFailed("write", e);
        }
    }

    private RecommendationResponse deserialize(String key, String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RecommendationResponse.class);
        } catch (JsonProcessingException e) {
            // E.g. written by an older DTO shape; recompute and overwrite it.
            log.warn("Unreadable recommendation cache entry {}; recomputing", key, e);
            return null;
        }
    }

    Duration jitteredTtl(String strategy) {
        Duration base = properties.ttlFor(strategy);
        double jitter = properties.getTtlJitter();
        double factor = jitter > 0 ? 1 + ThreadLocalRandom.current().nextDouble(-jitter, jitter) : 1;
        return Duration.ofMillis(Math.max(1000, Math.round(base.toMillis() * factor)));
    }

    private void bumpGenerationAfterCommit(String scope, String generationKey) {
        Runnable bump = () -> bumpGeneration(scope, generationKey);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bump.run();
                }
            });
        } else {
            bump.run();
        }
    }

    private void bumpGeneration(String scope, String generationKey) {
        if (!properties.isEnabled()) {
            return;
        }
        // Attempted even while reads back off: a missed invalidation serves stale results until the TTL.
        try {
            store.set(generationKey, newGeneration(), properties.getGenerationTtl());
            counter("recommendation.cache.invalidations", "scope", scope).increment();
        } catch (RuntimeException e) {
            redisFailed("invalidate", e);
            log.warn("Failed to invalidate {}; cached entries will expire on their TTL", generationKey);
        }
    }

    /** Random rather than a counter so a generation key that expired and was recreated can't repeat a value. */
    private static String newGeneration() {
        return Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, 36);
    }

    private boolean isBackingOff() {
        if (!redisBackingOff) {
            return false;
        }
        if (System.nanoTime() - redisRetryAtNanos >= 0) {
            redisBackingOff = false;
            return false;
        }
        return true;
    }

    private void redisFailed(String operation, RuntimeException e) {
        counter("recommendation.cache.errors", "operation", operation).increment();
        Duration backoff = properties.getFailureBackoff();
        if (!backoff.isZero() && !backoff.isNegative()) {
            redisRetryAtNanos = System.nanoTime() + backoff.toNanos();
            redisBackingOff = true;
        }
        log.warn("Recommendation cache {} failed; serving computed results and skipping the cache for {}",
                operation, backoff, e);
    }

    private void record(String endpoint, String result, long startNanos) {
        counter("recommendation.cache.requests", "endpoint", endpoint, "result", result).increment();
        timer("recommendation.cache.latency", "endpoint", endpoint, "result", result)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).publishPercentiles(0.5, 0.95).register(meterRegistry);
    }
}
