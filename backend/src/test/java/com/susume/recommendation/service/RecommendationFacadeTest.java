package com.susume.recommendation.service;

import com.susume.recommendation.client.RecommendationRankingClient;
import com.susume.recommendation.dto.*;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import com.susume.recommendation.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendationFacadeTest {

    private final RecommendationStrategyResolver strategyResolver = mock(RecommendationStrategyResolver.class);
    private final CandidateAggregator candidateAggregator = mock(CandidateAggregator.class);
    private final RecommendationRankingClient rankingClient = mock(RecommendationRankingClient.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final RecommendationFacade facade = new RecommendationFacade(strategyResolver,
            mock(RecommendationHistoryService.class), candidateAggregator, rankingClient, itemRepository);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        for (String name : List.of("personalized", "trending")) {
            RecommendationStrategy strategy = mock(RecommendationStrategy.class);
            when(strategy.getName()).thenReturn(name);
            when(strategy.recommend(any())).thenReturn(RecommendationResult.builder()
                    .strategy(name)
                    .items(List.of(item("s-1", 0.9), item("s-2", 0.8), item("s-3", 0.7)))
                    .build());
            when(strategyResolver.resolve(name)).thenReturn(strategy);
        }
        when(candidateAggregator.aggregateCandidates(any())).thenReturn(List.of(
                RecommendationCandidate.builder().itemId("s-1").strategyScores(Map.of()).build()));
        when(itemRepository.findActiveByTenantIdAndExternalItemId(eq(tenantId), anyString()))
                .thenAnswer(inv -> inv.getArgument(1, String.class).startsWith("inactive")
                        ? null : Item.builder().metadata(Map.of("title", inv.getArgument(1))).build());
    }

    private static RecommendationItemResponse item(String id, double score) {
        return RecommendationItemResponse.builder().externalItemId(id).similarityScore(score).build();
    }

    private RecommendationContext context(int limit) {
        return RecommendationContext.builder().tenantId(tenantId).externalUserId(userId).limit(limit).build();
    }

    private static RankResponse rankResponse(String mode, String... ids) {
        double score = 1.0;
        var ranked = new java.util.ArrayList<RankResponse.RankedRecommendation>();
        for (String id : ids) {
            ranked.add(new RankResponse.RankedRecommendation(id, score -= 0.1));
        }
        return new RankResponse(ranked, new RankResponse.ModelMetadata("susume-ranker", "v2"), mode, ids.length);
    }

    @Test
    void usesModelRankingSkipsInactiveItemsAndCapsToLimit() {
        when(rankingClient.rankCandidates(any()))
                .thenReturn(Optional.of(rankResponse("ml", "ml-1", "inactive-1", "ml-2", "ml-3")));

        RecommendationResponse response = facade.executeRecommendation("personalized", context(2));

        assertEquals(List.of("ml-1", "ml-2"),
                response.getRecommendations().stream().map(RecommendationItemResponse::getExternalItemId).toList());
        assertEquals("personalized_ml_reranked", response.getStrategy());
        assertEquals("v2", response.getModelVersion());
        assertEquals(Map.of("title", "ml-1"), response.getRecommendations().get(0).getMetadata());
    }

    @Test
    void sendsTenantUserAndCandidateIdsAndOverFetches() {
        when(rankingClient.rankCandidates(any())).thenReturn(Optional.empty());

        facade.executeRecommendation("personalized", context(4));

        ArgumentCaptor<RankRequest> sent = ArgumentCaptor.forClass(RankRequest.class);
        verify(rankingClient).rankCandidates(sent.capture());
        assertEquals(tenantId.toString(), sent.getValue().getTenantId());
        assertEquals(userId.toString(), sent.getValue().getUser().getId());
        assertEquals("s-1", sent.getValue().getCandidates().get(0).getItemId());
        assertEquals(12, sent.getValue().getLimit());
    }

    @Test
    void labelsColdStartRanking() {
        when(rankingClient.rankCandidates(any())).thenReturn(Optional.of(rankResponse("cold_start_popularity", "p-1")));

        RecommendationResponse response = facade.executeRecommendation("personalized", context(5));

        assertEquals("personalized_cold_start_popularity", response.getStrategy());
    }

    @Test
    void fallsBackToStrategyOrderWhenRankerUnavailable() {
        when(rankingClient.rankCandidates(any())).thenReturn(Optional.empty());

        RecommendationResponse response = facade.executeRecommendation("personalized", context(2));

        assertEquals(List.of("s-1", "s-2"),
                response.getRecommendations().stream().map(RecommendationItemResponse::getExternalItemId).toList());
        assertEquals("personalized", response.getStrategy());
        assertEquals("spring-baseline", response.getModelVersion());
    }

    @Test
    void fallsBackWhenEveryRankedItemIsInactive() {
        when(rankingClient.rankCandidates(any())).thenReturn(Optional.of(rankResponse("ml", "inactive-1")));

        RecommendationResponse response = facade.executeRecommendation("personalized", context(2));

        assertEquals("personalized", response.getStrategy());
    }

    @Test
    void doesNotRerankOtherStrategies() {
        RecommendationResponse response = facade.executeRecommendation("trending", context(2));

        verifyNoInteractions(rankingClient);
        assertEquals("trending", response.getStrategy());
    }
}
