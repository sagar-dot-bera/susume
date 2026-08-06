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
public class TenantStrategyConfigDto {
    private String defaultEngine; // e.g. "HYBRID"
    private String activeTarget; // e.g. "HYBRID_V2_STABLE"
    private Map<String, Double> interactionWeights; // e.g. {"click": 0.15, "like": 0.35, "purchase": 0.50}
    private Map<String, Object> advancedParameters; // e.g. {"similarityThreshold": 0.82, "neighborCount": 150, "confidenceLimit": 95, "recLimit": 50}
    private Map<String, Integer> hybridBlend; // e.g. {"cfWeight": 45, "contentWeight": 45, "trendWeight": 10}
    private Map<String, Boolean> enabledStrategies;
}
