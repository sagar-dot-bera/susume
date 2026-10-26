package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationRequest;
import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.dto.RecordInteractionRequest;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationImpression;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.repository.RecommendationImpressionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the key recommendations are cached under is the same key
 * recording an interaction evicts (and that the Python interaction-service evicts).
 */
@SpringJUnitConfig(RecommendationCacheTest.CacheTestConfig.class)
class RecommendationCacheTest {

    @Configuration
    @EnableCaching
    static class CacheTestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("recommendation");
        }

        @Bean
        RecommendationFacade recommendationFacade() {
            return mock(RecommendationFacade.class);
        }

        @Bean
        InteractionRepository interactionRepository() {
            return mock(InteractionRepository.class);
        }

        @Bean
        ItemRepository itemRepository() {
            return mock(ItemRepository.class);
        }

        @Bean
        RecommendationImpressionRepository impressionRepository() {
            return mock(RecommendationImpressionRepository.class);
        }

        @Bean
        PersonalizedRecommendationProvider personalizedRecommendationProvider(RecommendationFacade facade) {
            return new PersonalizedRecommendationProvider(facade, 50);
        }

        @Bean
        RecommendationService recommendationService(RecommendationFacade facade,
                PersonalizedRecommendationProvider provider, RecommendationImpressionRepository impressions) {
            return new RecommendationService(facade, provider, new ImpressionLogger(impressions), 50);
        }

        @Bean
        RecommendationCacheEvictor recommendationCacheEvictor(CacheManager cacheManager) {
            return new RecommendationCacheEvictor(cacheManager);
        }

        @Bean
        InteractionService interactionService(InteractionRepository interactions, ItemRepository items,
                RecommendationCacheEvictor evictor) {
            return new InteractionService(interactions, items, evictor);
        }
    }

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private RecommendationFacade facade;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private InteractionRepository interactionRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RecommendationImpressionRepository impressionRepository;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cacheManager.getCache("recommendation").clear();
        reset(facade, itemRepository, interactionRepository, impressionRepository);
        when(facade.executeRecommendation(eq("personalized"), any()))
                .thenReturn(new RecommendationResponse(userId,
                        List.of(new RecommendationItemResponse("item-1", null, 0.9)), "personalized_ml_reranked", "v2"));
        when(itemRepository.findByTenantIdAndExternalItemId(any(), any()))
                .thenReturn(Optional.of(Item.builder().status("ACTIVE").build()));
        when(interactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void cachesRecommendationsUnderTenantAndUserKey() {
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);

        verify(facade, times(1)).executeRecommendation(eq("personalized"), any());
        assertNotNull(cacheManager.getCache("recommendation").get(tenantId + ":" + userId + ":10"));
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
    void cachesEachLimitSeparately() {
        recommendationService.getRecommendations(new RecommendationRequest(userId, 5), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(userId, 20), tenantId);

        verify(facade, times(2)).executeRecommendation(eq("personalized"), any());
        var cache = cacheManager.getCache("recommendation");
        assertNotNull(cache.get(tenantId + ":" + userId + ":5"));
        assertNotNull(cache.get(tenantId + ":" + userId + ":20"));
    }

    @Test
    void doesNotShareCacheEntriesAcrossTenants() {
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(userId, 10), UUID.randomUUID());

        verify(facade, times(2)).executeRecommendation(eq("personalized"), any());
    }

    @Test
    void recordingAnInteractionEvictsEveryLimitForThatUserOnly() {
        UUID otherUser = UUID.randomUUID();
        recommendationService.getRecommendations(new RecommendationRequest(userId, 5), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(userId, 20), tenantId);
        recommendationService.getRecommendations(new RecommendationRequest(otherUser, 5), tenantId);

        interactionService.recordInteraction(tenantId,
                new RecordInteractionRequest(userId.toString(), "item-1", "CLICK", null));

        var cache = cacheManager.getCache("recommendation");
        assertNull(cache.get(tenantId + ":" + userId + ":5"));
        assertNull(cache.get(tenantId + ":" + userId + ":20"));
        assertNotNull(cache.get(tenantId + ":" + otherUser + ":5"));
    }

    @Test
    void escapesGlobCharactersInUserIds() {
        assertEquals("t:a\\*b\\?c\\[d\\]:", RecommendationCacheEvictor.escapeGlob("t:a*b?c[d]:"));
    }
}
