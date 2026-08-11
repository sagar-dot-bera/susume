package com.susume.recommendation.service;

import com.susume.recommendation.client.RecommendationRankingClient;
import com.susume.recommendation.dto.*;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationImpression;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.repository.RecommendationImpressionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RecommendationFacade {

    private final RecommendationStrategyResolver strategyResolver;
    private final RecommendationHistoryService historyService;
    private final CandidateAggregator candidateAggregator;
    private final RecommendationRankingClient rankingClient;
    private final RecommendationImpressionRepository impressionRepository;
    private final ItemRepository itemRepository;

    public RecommendationFacade(
            RecommendationStrategyResolver strategyResolver,
            RecommendationHistoryService historyService,
            CandidateAggregator candidateAggregator,
            RecommendationRankingClient rankingClient,
            RecommendationImpressionRepository impressionRepository,
            ItemRepository itemRepository) {
        this.strategyResolver = strategyResolver;
        this.historyService = historyService;
        this.candidateAggregator = candidateAggregator;
        this.rankingClient = rankingClient;
        this.impressionRepository = impressionRepository;
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

        // Phase 2 Candidate Aggregation and ML Re-Ranking
        try {
            List<RecommendationCandidate> candidates = candidateAggregator.aggregateCandidates(context);
            if (!candidates.isEmpty()) {
                Map<String, Object> userFeatures = candidateAggregator.buildUserFeatures(
                        context.getTenantId(), context.getExternalUserId());

                RankRequest rankRequest = RankRequest.builder()
                        .user(RankRequest.UserPayload.builder()
                                .id(context.getExternalUserId() != null ? context.getExternalUserId().toString() : "anonymous")
                                .features(userFeatures)
                                .build())
                        .context(RankRequest.ContextPayload.builder()
                                .surface(context.getSurface() != null ? context.getSurface() : "default")
                                .hour(context.getHour() != null ? context.getHour() : Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                                .dayOfWeek(context.getDayOfWeek() != null ? context.getDayOfWeek() : Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
                                .build())
                        .candidates(candidates)
                        .limit(context.getEffectiveLimit(10))
                        .build();

                Optional<RankResponse> rankResponseOpt = rankingClient.rankCandidates(rankRequest);
                if (rankResponseOpt.isPresent()) {
                    RankResponse rankResponse = rankResponseOpt.get();
                    if (rankResponse.getRecommendations() != null && !rankResponse.getRecommendations().isEmpty()) {
                        Map<String, RecommendationCandidate> candidateMap = candidates.stream()
                                .collect(Collectors.toMap(RecommendationCandidate::getItemId, Function.identity(), (a, b) -> a));

                        List<RecommendationItemResponse> mlRankedItems = new ArrayList<>();
                        for (RankResponse.RankedRecommendation rankedItem : rankResponse.getRecommendations()) {
                            Item item = itemRepository.findActiveByTenantIdAndExternalItemId(context.getTenantId(), rankedItem.getItemId());
                            Object metadata = item != null ? item.getMetadata() : null;
                            mlRankedItems.add(RecommendationItemResponse.builder()
                                    .externalItemId(rankedItem.getItemId())
                                    .metadata(metadata)
                                    .similarityScore(rankedItem.getScore())
                                    .build());
                        }

                        if (!mlRankedItems.isEmpty()) {
                            finalRecommendations = mlRankedItems;
                            usedStrategy = strategy.getName() + "_ml_reranked";
                            if (rankResponse.getModel() != null && rankResponse.getModel().getVersion() != null) {
                                modelVersion = rankResponse.getModel().getVersion();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error during candidate aggregation / ML re-ranking: {}. Falling back to default Spring recommendation.", e.getMessage());
        }

        // Cap final recommendations to requested limit
        int limit = context.getEffectiveLimit(10);
        if (finalRecommendations.size() > limit) {
            finalRecommendations = finalRecommendations.subList(0, limit);
        }

        // Impression Logging
        logImpressions(context, finalRecommendations, modelVersion);

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
                .build();
    }

    private void logImpressions(RecommendationContext context, List<RecommendationItemResponse> items, String modelVersion) {
        if (items == null || items.isEmpty()) return;
        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        List<RecommendationImpression> impressions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            RecommendationItemResponse item = items.get(i);
            impressions.add(RecommendationImpression.builder()
                    .requestId(requestId)
                    .tenantId(context.getTenantId())
                    .userId(context.getExternalUserId() != null ? context.getExternalUserId().toString() : null)
                    .itemId(item.getExternalItemId())
                    .position(i + 1)
                    .timestamp(now)
                    .modelVersion(modelVersion)
                    .strategyScores(Map.of("score", item.getSimilarityScore()))
                    .build());
        }

        try {
            impressionRepository.saveAll(impressions);
        } catch (Exception e) {
            log.warn("Failed to persist recommendation impressions: {}", e.getMessage());
        }
    }
}
