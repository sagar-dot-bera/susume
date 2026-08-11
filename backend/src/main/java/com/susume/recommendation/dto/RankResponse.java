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
