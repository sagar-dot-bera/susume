package com.susume.recommendation.service;

import com.susume.recommendation.client.RecommendationRankingClient;
import com.susume.recommendation.dto.*;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import com.susume.recommendation.repository.ItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class RecommendationFacade {

    private final RecommendationStrategyResolver strategyResolver;
    private final RecommendationHistoryService historyService;
    private final CandidateAggregator candidateAggregator;
    private final RecommendationRankingClient rankingClient;
    private final ItemRepository itemRepository;

    public RecommendationFacade(
            RecommendationStrategyResolver strategyResolver,
            RecommendationHistoryService historyService,
            CandidateAggregator candidateAggregator,
            RecommendationRankingClient rankingClient,
            ItemRepository itemRepository) {
        this.strategyResolver = strategyResolver;
        this.historyService = historyService;
        this.candidateAggregator = candidateAggregator;
        this.rankingClient = rankingClient;
        this.itemRepository = itemRepository;
    }

    public RecommendationResponse executeRecommendation(String strategyName, RecommendationContext context) {
        long start = System.nanoTime();
        RecommendationStrategy strategy = strategyResolver.resolve(strategyName);
        RecommendationResult result = strategy.recommend(context);

        // Fallback to trending if cold-start or empty results
        if ((result.getItems() == null || result.getItems().isEmpty()) && !"trending".equalsIgnoreCase(strategy.getName())) {
            log.info("Primary strategy '{}' returned empty results for tenant {}. Falling back to 'trending'.",
                    strategy.getName(), context.getTenantId());
            RecommendationStrategy trendingStrat = strategyResolver.resolve("trending");
            result = trendingStrat.recommend(context);
        }

        List<RecommendationItemResponse> finalRecommendations = result.getItems() != null ? result.getItems() : Collections.emptyList();
        String modelVersion = "spring-baseline";
        String usedStrategy = result.getStrategy();

        // ML re-ranking applies to personalized requests for a known user; other strategies
        // (trending, admin previews of one strategy) are returned as the strategy ordered them.
        if ("personalized".equalsIgnoreCase(strategyName) && context.getExternalUserId() != null) {
            try {
                Optional<MlRanking> ml = rankWithModel(context);
                if (ml.isPresent()) {
                    finalRecommendations = ml.get().items();
                    usedStrategy = strategy.getName() + ("ml".equals(ml.get().mode()) ? "_ml_reranked" : "_" + ml.get().mode());
                    modelVersion = ml.get().modelVersion();
                }
            } catch (Exception e) {
                log.warn("Error during candidate aggregation / ML re-ranking: {}. Falling back to default Spring recommendation.", e.getMessage());
            }
        }

        // Cap final recommendations to requested limit
        int limit = context.getEffectiveLimit(10);
        if (finalRecommendations.size() > limit) {
            finalRecommendations = finalRecommendations.subList(0, limit);
        }

        long executionTimeMs = (System.nanoTime() - start) / 1_000_000;

        historyService.saveRecommendationHistory(
                context.getExternalUserId() != null ? context.getExternalUserId().toString() : null,
                context.getTenantId(),
                usedStrategy,
                (int) executionTimeMs,
                finalRecommendations
        );

        return RecommendationResponse.builder()
                .userId(context.getExternalUserId())
                .recommendations(finalRecommendations)
                .strategy(usedStrategy)
                .modelVersion(modelVersion)
                .build();
    }

    private record MlRanking(List<RecommendationItemResponse> items, String mode, String modelVersion) {
    }

    /**
     * Asks the ML service to rank the strategies' candidates (it also adds its own, generated the
     * same way as in training). Empty when the service is unavailable or returns nothing usable.
     */
    private Optional<MlRanking> rankWithModel(RecommendationContext context) {
        List<RecommendationCandidate> candidates = candidateAggregator.aggregateCandidates(context);
        int limit = context.getEffectiveLimit(10);

        RankRequest rankRequest = RankRequest.builder()
                .tenantId(context.getTenantId().toString())
                .user(RankRequest.UserPayload.builder().id(context.getExternalUserId().toString()).build())
                .context(RankRequest.ContextPayload.builder().surface(context.getSurface()).build())
                .candidates(candidates)
                // Over-fetch: ranked items the ML service knows from history may no longer be active.
                .limit(limit * 3)
                .build();

        Optional<RankResponse> response = rankingClient.rankCandidates(rankRequest);
        if (response.isEmpty() || response.get().getRecommendations() == null) {
            return Optional.empty();
        }

        List<RecommendationItemResponse> items = new ArrayList<>();
        for (RankResponse.RankedRecommendation ranked : response.get().getRecommendations()) {
            if (items.size() == limit) {
                break;
            }
            Item item = itemRepository.findActiveByTenantIdAndExternalItemId(context.getTenantId(), ranked.getItemId());
            if (item == null) {
                continue;
            }
            items.add(RecommendationItemResponse.builder()
                    .externalItemId(ranked.getItemId())
                    .metadata(item.getMetadata())
                    .similarityScore(ranked.getScore())
                    .build());
        }
        if (items.isEmpty()) {
            return Optional.empty();
        }

        RankResponse ranked = response.get();
        String mode = ranked.getRankingMode() != null ? ranked.getRankingMode() : "ml";
        String version = ranked.getModel() != null && ranked.getModel().getVersion() != null
                ? ranked.getModel().getVersion() : "unknown";
        return Optional.of(new MlRanking(items, mode, version));
    }
}
