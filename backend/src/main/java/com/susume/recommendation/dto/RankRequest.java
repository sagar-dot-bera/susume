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
public class RankRequest {

    private UserPayload user;
    private ContextPayload context;
    private List<RecommendationCandidate> candidates;
    private int limit;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserPayload {
        private String id;
        private Map<String, Object> features;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContextPayload {
        private String surface;
        private Integer hour;
        private Integer dayOfWeek;
    }
}
