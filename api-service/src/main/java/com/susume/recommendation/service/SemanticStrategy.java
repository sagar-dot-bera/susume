package com.susume.recommendation.service;

import java.util.List;

import com.susume.recommendation.dto.RecommendationRequest;
import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.entity.RecommendationAlgorithm;

public class SemanticStrategy implements RecommendationStrategy {

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.SEMANTIC;
    }

    @Override
    public List<RecommendationResponse> recommend(RecommendationRequest request) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'recommend'");
    }

}
