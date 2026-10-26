package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationCandidate;
import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CandidateAggregatorTest {

    private RecommendationStrategyResolver strategyResolver;
    private CandidateAggregator aggregator;
    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        strategyResolver = mock(RecommendationStrategyResolver.class);
        aggregator = new CandidateAggregator(strategyResolver);
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
}
