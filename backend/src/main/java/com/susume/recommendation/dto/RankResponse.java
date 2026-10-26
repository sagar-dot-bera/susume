package com.susume.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankResponse {

    private List<RankedRecommendation> recommendations;
    private ModelMetadata model;
    // "ml" or "cold_start_popularity"
    private String rankingMode;
    private int candidateCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RankedRecommendation {
        private String itemId;
        private double score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModelMetadata {
        private String name;
        private String version;
    }
}
