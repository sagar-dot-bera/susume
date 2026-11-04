package com.susume.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.susume.recommendation.cache.RecommendationCache;
import com.susume.recommendation.cache.RecommendationCacheProperties;
import com.susume.recommendation.cache.RecommendationCacheStore;
import com.susume.recommendation.client.RecommendationRankingClient;
import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationRequest;
import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.dto.RecordInteractionRequest;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationImpression;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.repository.RecommendationImpressionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The cache wired into the request path: what is cached, what is logged, and what invalidates it. */
class RecommendationServiceCacheTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private RecommendationFacade facade;
    private RecommendationRankingClient rankingClient;
    private RecommendationImpressionRepository impressionRepository;
    private RecommendationService recommendationService;
    private InteractionService interactionService;

    @BeforeEach
    void setUp() {
        Map<String, String> redis = new HashMap<>();
        RecommendationCacheStore store = new RecommendationCacheStore() {
            @Override
            public List<String> multiGet(List<String> keys) {
                List<String> out = new ArrayList<>();
                keys.forEach(k -> out.add(redis.get(k)));
                return out;
            }

            @Override
            public void set(String key, String value, Duration ttl) {
                redis.put(key, value);
            }
        };
        RecommendationCache cache = new RecommendationCache(store, new RecommendationCacheProperties(),
                new ObjectMapper(), new SimpleMeterRegistry());

        facade = mock(RecommendationFacade.class);
        rankingClient = mock(RecommendationRankingClient.class);
        impressionRepository = mock(RecommendationImpressionRepository.class);
        ItemRepository itemRepository = mock(ItemRepository.class);
        InteractionRepository interactionRepository = mock(InteractionRepository.class);

        when(rankingClient.currentModelVersion()).thenReturn("v2");
        when(facade.executeRecommendation(any(), any())).thenAnswer(inv -> new RecommendationResponse(userId,
                List.of(new RecommendationItemResponse("item-1", null, 0.9)), "personalized_ml_reranked", "v2"));
        when(itemRepository.findByTenantIdAndExternalItemId(any(), any()))
                .thenReturn(Optional.of(Item.builder().status("ACTIVE").build()));
        when(interactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        recommendationService = new RecommendationService(facade, cache, rankingClient,
                new ImpressionLogger(impressionRepository), 50);
        interactionService = new InteractionService(interactionRepository, itemRepository, cache);
    }

    @Test
    void logsImpressionsForCacheHitsToo() {
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);

        verify(facade, times(1)).executeRecommendation(eq("personalized"), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecommendationImpression>> saved = ArgumentCaptor.forClass(List.class);
        verify(impressionRepository, times(2)).saveAll(saved.capture());
        RecommendationImpression impression = saved.getValue().get(0);
        assertEquals("item-1", impression.getItemId());
        assertEquals(1, impression.getPosition());
        assertEquals("v2", impression.getModelVersion());
        assertEquals(userId.toString(), impression.getUserId());
        assertNotEquals(saved.getAllValues().get(0).get(0).getRequestId(), impression.getRequestId());
    }

    @Test
    void equivalentLimitsShareOneEntry() {
        // 0 means the default (10) and limits above the max (50) are capped, so these four requests need two entries.
        recommendationService.getRecommendations(new RecommendationRequest(userId, 0), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(userId, 500), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(userId, 50), tenantId);

        ArgumentCaptor<RecommendationContext> contexts = ArgumentCaptor.forClass(RecommendationContext.class);
        verify(facade, times(2)).executeRecommendation(eq("personalized"), contexts.capture());
        assertEquals(List.of(10, 50), contexts.getAllValues().stream().map(RecommendationContext::getLimit).toList());
    }

    @Test
    void recordingAnInteractionInvalidatesThatUsersRecommendations() {
        UUID otherUser = UUID.randomUUID();
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(otherUser, 10), tenantId);

        interactionService.recordInteraction(tenantId,
                new RecordInteractionRequest(userId.toString(), "item-1", "CLICK", null));

        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(otherUser, 10), tenantId);
        verify(facade, times(3)).executeRecommendation(eq("personalized"), any());
    }

    @Test
    void newModelVersionStopsServingOldEntries() {
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);
        when(rankingClient.currentModelVersion()).thenReturn("v3");
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);

        verify(facade, times(2)).executeRecommendation(eq("personalized"), any());
    }

    @Test
    void trendingIsCachedPerLimitAndWindow() {
        recommendationService.getTrending(tenantId, 10, 30);
        recommendationService.getTrending(tenantId, 10, 30);
        recommendationService.getTrending(tenantId, 10, 7);

        verify(facade, times(2)).executeRecommendation(eq("trending"), any());
        verify(impressionRepository, times(3)).saveAll(any());
    }

    @Test
    void adminPreviewsAreNeverCached() {
        RecommendationContext context = RecommendationContext.builder().tenantId(tenantId).build();
        recommendationService.getRecommendationsByStrategy("popularity", context);
        recommendationService.getRecommendationsByStrategy("popularity", context);

        verify(facade, times(2)).executeRecommendation(eq("popularity"), any());
    }
}
