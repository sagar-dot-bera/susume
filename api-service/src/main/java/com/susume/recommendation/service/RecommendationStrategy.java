package com.susume.recommendation.service;

import java.util.List;

import com.susume.recommendation.dto.RecommendationRequest;
import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.entity.RecommendationAlgorithm;

public interface RecommendationStrategy {
    RecommendationAlgorithm getAlgorithm();

    List<RecommendationResponse> recommend(
            RecommendationRequest request);
}
