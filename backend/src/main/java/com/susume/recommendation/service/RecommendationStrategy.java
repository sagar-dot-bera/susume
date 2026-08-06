package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.RecommendationAlgorithm;

public interface RecommendationStrategy {
    RecommendationAlgorithm getAlgorithm();

    String getName();

    RecommendationResult recommend(RecommendationContext context);

    default boolean supports(String strategyName) {
        return getName().equalsIgnoreCase(strategyName) ||
                getAlgorithm().name().equalsIgnoreCase(strategyName);
    }
}
