package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class SemanticStrategy implements RecommendationStrategy {

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.SEMANTIC;
    }

    @Override
    public String getName() {
        return "semantic";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        return RecommendationResult.builder()
                .strategy(getName())
                .items(Collections.emptyList())
                .executionTimeMs(0)
                .build();
    }
}
