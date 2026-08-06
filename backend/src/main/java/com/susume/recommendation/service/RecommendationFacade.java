package com.susume.recommendation.service;

import com.susume.recommendation.dto.*;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class RecommendationFacade {

    private final RecommendationStrategyResolver strategyResolver;
    private final RecommendationHistoryService historyService;

    public RecommendationFacade(RecommendationStrategyResolver strategyResolver, RecommendationHistoryService historyService) {
        this.strategyResolver = strategyResolver;
        this.historyService = historyService;
    }

    public RecommendationResponse executeRecommendation(String strategyName, RecommendationContext context) {
        RecommendationStrategy strategy = strategyResolver.resolve(strategyName);
        RecommendationResult result = strategy.recommend(context);

        // Fallback to trending if cold-start or empty results
        if ((result.getItems() == null || result.getItems().isEmpty()) && !"trending".equalsIgnoreCase(strategy.getName())) {
            log.info("Primary strategy '{}' returned empty results for tenant {}. Falling back to 'trending'.",
                    strategy.getName(), context.getTenantId());
            RecommendationStrategy trendingStrat = strategyResolver.resolve("trending");
            result = trendingStrat.recommend(context);
        }

        historyService.saveRecommendationHistory(
                context.getExternalUserId() != null ? context.getExternalUserId().toString() : null,
                context.getTenantId(),
                result.getStrategy(),
                (int) result.getExecutionTimeMs(),
                result.getItems()
        );

        return RecommendationResponse.builder()
                .userId(context.getExternalUserId())
                .recommendations(result.getItems())
                .strategy(result.getStrategy())
                .build();
    }
}
