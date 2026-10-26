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
public class RankRequest {

    private String tenantId;
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
