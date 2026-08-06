package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import com.susume.recommendation.framework.RankingEngine;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import com.susume.recommendation.service.RecommendationStrategy;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class HybridStrategy implements RecommendationStrategy {

    private final RecommendationStrategyResolver resolver;
    private final RankingEngine rankingEngine = new RankingEngine();

    public HybridStrategy(@Lazy RecommendationStrategyResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.HYBRID;
    }

    @Override
    public String getName() {
        return "hybrid";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        long start = System.nanoTime();

        Map<String, Double> strategyWeights = Map.of(
                "personalized", 0.4,
                "content_based", 0.3,
                "popularity", 0.2,
                "trending", 0.1
        );

        if (context.getTenantConfig() != null && context.getTenantConfig().containsKey("weights")) {
            Object rawWeights = context.getTenantConfig().get("weights");
            if (rawWeights instanceof Map<?, ?> customWeightsMap) {
                Map<String, Double> parsedMap = new HashMap<>();
                customWeightsMap.forEach((k, v) -> {
                    if (v instanceof Number n) {
                        parsedMap.put(k.toString(), n.doubleValue());
                    }
                });
                if (!parsedMap.isEmpty()) {
                    strategyWeights = parsedMap;
                }
            }
        }

        Map<String, RecommendationItemResponse> combinedScoredItems = new HashMap<>();

        for (Map.Entry<String, Double> entry : strategyWeights.entrySet()) {
            String stratName = entry.getKey();
            double weight = entry.getValue();

            try {
                RecommendationStrategy strategy = resolver.resolve(stratName);
                RecommendationResult res = strategy.recommend(context);
                if (res != null && res.getItems() != null) {
                    List<RecommendationItemResponse> normalized = rankingEngine.normalizeScores(res.getItems());
                    for (RecommendationItemResponse item : normalized) {
                        String id = item.getExternalItemId();
                        double weightedScore = item.getSimilarityScore() * weight;

                        combinedScoredItems.compute(id, (k, existing) -> {
                            if (existing == null) {
                                return RecommendationItemResponse.builder()
                                        .externalItemId(item.getExternalItemId())
                                        .metadata(item.getMetadata())
                                        .similarityScore(weightedScore)
                                        .build();
                            } else {
                                existing.setSimilarityScore(existing.getSimilarityScore() + weightedScore);
                                return existing;
                            }
                        });
                    }
                }
            } catch (Exception e) {
                // Ignore fallback error for sub-strategy
            }
        }

        List<RecommendationItemResponse> items = rankingEngine.rankAndCap(
                new ArrayList<>(combinedScoredItems.values()), context.getEffectiveLimit(10));

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return RecommendationResult.builder()
                .strategy(getName())
                .items(items)
                .executionTimeMs(latencyMs)
                .metadata(Map.of("weightsUsed", strategyWeights))
                .build();
    }
}
