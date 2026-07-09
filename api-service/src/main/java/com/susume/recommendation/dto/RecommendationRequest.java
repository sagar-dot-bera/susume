package com.susume.recommendation.dto;

public record RecommendationRequest(
        String tenantId, String externalUserId, int limit) {

}
