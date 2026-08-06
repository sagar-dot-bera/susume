package com.susume.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResult {
    private String strategy;
    private List<RecommendationItemResponse> items;
    private long executionTimeMs;
    private Map<String, Object> metadata;
}
