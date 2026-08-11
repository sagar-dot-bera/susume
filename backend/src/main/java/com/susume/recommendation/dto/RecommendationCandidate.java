package com.susume.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationCandidate {
    private String itemId;
    private Map<String, Double> strategyScores;
    private Map<String, Object> itemFeatures;
    private Map<String, Object> userItemFeatures;
}
