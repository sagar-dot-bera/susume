package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationCandidate;
import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Collects candidate items from every strategy for the ML ranker. Only the item ids are used
 * for ranking: the recommendation-service computes all model features itself from raw
 * interaction events, with the same code as training. Strategy scores are kept for logging
 * and for ordering when the ranker is unavailable.
 */
@Slf4j
@Service
public class CandidateAggregator {

    public static final List<String> ALL_STRATEGIES = List.of(
            "contentBased",
            "collaborativeFiltering",
            "frequentlyBoughtTogether",
            "hybrid",
            "personalized",
            "popularity",
            "randomDiscovery",
            "ruleBased",
            "similarItems",
            "trending"
    );

    private final RecommendationStrategyResolver strategyResolver;

    public CandidateAggregator(RecommendationStrategyResolver strategyResolver) {
        this.strategyResolver = strategyResolver;
    }

    public List<RecommendationCandidate> aggregateCandidates(RecommendationContext context) {
        int candidatePoolSize = context.getEffectiveCandidatePoolSize(100);
        RecommendationContext candidateContext = RecommendationContext.builder()
                .tenantId(context.getTenantId())
                .externalUserId(context.getExternalUserId())
                .externalItemId(context.getExternalItemId())
                .limit(candidatePoolSize)
                .candidatePoolSize(candidatePoolSize)
                .filters(context.getFilters())
                .tenantConfig(context.getTenantConfig())
                .stateData(context.getStateData())
                .surface(context.getSurface())
                .hour(context.getHour())
                .dayOfWeek(context.getDayOfWeek())
                .build();

        List<RecommendationStrategy> strategies = strategyResolver.getAllStrategies();
        Map<String, Map<String, Double>> candidateScores = new LinkedHashMap<>();

        for (RecommendationStrategy strategy : strategies) {
            try {
                RecommendationResult result = strategy.recommend(candidateContext);
                if (result != null && result.getItems() != null) {
                    String canonicalName = toCanonicalStrategyName(strategy.getName());
                    for (RecommendationItemResponse item : result.getItems()) {
                        candidateScores
                                .computeIfAbsent(item.getExternalItemId(), k -> new HashMap<>())
                                .put(canonicalName, item.getSimilarityScore());
                    }
                }
            } catch (Exception e) {
                log.warn("Strategy '{}' failed during candidate generation: {}", strategy.getName(), e.getMessage());
            }
        }

        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> entry : candidateScores.entrySet()) {
            Map<String, Double> strategyScores = new HashMap<>();
            for (String stratKey : ALL_STRATEGIES) {
                strategyScores.put(stratKey, entry.getValue().getOrDefault(stratKey, 0.0));
            }
            candidates.add(RecommendationCandidate.builder()
                    .itemId(entry.getKey())
                    .strategyScores(strategyScores)
                    .build());
        }

        return candidates;
    }

    private String toCanonicalStrategyName(String rawName) {
        if (rawName == null) return "unknown";
        String clean = rawName.replace("_", "").replace("-", "").toLowerCase();
        return switch (clean) {
            case "contentbased" -> "contentBased";
            case "collaborativefiltering" -> "collaborativeFiltering";
            case "frequentlyboughttogether" -> "frequentlyBoughtTogether";
            case "hybrid" -> "hybrid";
            case "personalized" -> "personalized";
            case "popularity" -> "popularity";
            case "randomdiscovery" -> "randomDiscovery";
            case "rulebased" -> "ruleBased";
            case "similaritems" -> "similarItems";
            case "trending" -> "trending";
            default -> rawName;
        };
    }
}
