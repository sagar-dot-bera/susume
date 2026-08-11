package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationCandidate;
import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CandidateAggregatorTest {

    private RecommendationStrategyResolver strategyResolver;
    private InteractionRepository interactionRepository;
    private ItemRepository itemRepository;
    private CandidateAggregator aggregator;
    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        strategyResolver = mock(RecommendationStrategyResolver.class);
        interactionRepository = mock(InteractionRepository.class);
        itemRepository = mock(ItemRepository.class);
        aggregator = new CandidateAggregator(strategyResolver, interactionRepository, itemRepository);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void testAggregateCandidatesPreservesStrategyScoresAndDefaultsMissingToZero() {
        RecommendationStrategy popStrategy = mock(RecommendationStrategy.class);
        when(popStrategy.getName()).thenReturn("popularity");
        when(popStrategy.recommend(any())).thenReturn(RecommendationResult.builder()
                .strategy("popularity")
                .items(List.of(
                        RecommendationItemResponse.builder().externalItemId("item-1").similarityScore(0.85).build()
                ))
                .build());

        RecommendationStrategy cbStrategy = mock(RecommendationStrategy.class);
        when(cbStrategy.getName()).thenReturn("contentBased");
        when(cbStrategy.recommend(any())).thenReturn(RecommendationResult.builder()
                .strategy("contentBased")
                .items(List.of(
                        RecommendationItemResponse.builder().externalItemId("item-1").similarityScore(0.92).build(),
                        RecommendationItemResponse.builder().externalItemId("item-2").similarityScore(0.70).build()
                ))
                .build());

        when(strategyResolver.getAllStrategies()).thenReturn(List.of(popStrategy, cbStrategy));

        RecommendationContext context = RecommendationContext.builder()
                .tenantId(tenantId)
                .externalUserId(userId)
                .candidatePoolSize(50)
                .build();

        List<RecommendationCandidate> candidates = aggregator.aggregateCandidates(context);

        assertNotNull(candidates);
        assertEquals(2, candidates.size());

        RecommendationCandidate candidate1 = candidates.stream()
                .filter(c -> c.getItemId().equals("item-1"))
                .findFirst()
                .orElseThrow();

        assertEquals(0.85, candidate1.getStrategyScores().get("popularity"));
        assertEquals(0.92, candidate1.getStrategyScores().get("contentBased"));
        assertEquals(0.0, candidate1.getStrategyScores().get("trending")); // Missing strategy defaults to 0.0
    }

    @Test
    void testBuildUserFeaturesColdStart() {
        Map<String, Object> features = aggregator.buildUserFeatures(tenantId, null);
        assertEquals(0, features.get("totalInteractions"));
        assertEquals(0, features.get("totalViews"));
    }

    @Test
    void testBuildUserFeaturesWithHistory() {
        Interaction i1 = Interaction.builder()
                .externalItemId("item-1")
                .interactionType(InteractionType.VIEW)
                .timestamp(java.time.Instant.now())
                .build();
        Interaction i2 = Interaction.builder()
                .externalItemId("item-1")
                .interactionType(InteractionType.LIKE)
                .timestamp(java.time.Instant.now())
                .build();

        when(interactionRepository.findByTenantIdAndExternalUserId(tenantId, userId.toString()))
                .thenReturn(List.of(i1, i2));

        Map<String, Object> features = aggregator.buildUserFeatures(tenantId, userId);
        assertEquals(2, features.get("totalInteractions"));
        assertEquals(1, features.get("totalViews"));
        assertEquals(1, features.get("totalLikes"));
    }
}
