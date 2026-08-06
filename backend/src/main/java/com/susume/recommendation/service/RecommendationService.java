package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationRequest;
import com.susume.recommendation.dto.RecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class RecommendationService {

    private final RecommendationFacade recommendationFacade;
    private final int maxRecommendationLimit;

    public RecommendationService(
            RecommendationFacade recommendationFacade,
            @Value("${recommendation.max-limit:50}") int maxRecommendationLimit) {
        this.recommendationFacade = recommendationFacade;
        this.maxRecommendationLimit = maxRecommendationLimit;
    }

    @Cacheable(value = "recommendation", key = "#tenantId" + "#request.getExternalUserId()")
    public RecommendationResponse getRecommendations(RecommendationRequest request, UUID tenantId) {
        int cappedLimit = Math.min(request.limit() > 0 ? request.limit() : 10, maxRecommendationLimit);
        RecommendationContext context = RecommendationContext.builder()
                .tenantId(tenantId)
                .externalUserId(request.externalUserId())
                .limit(cappedLimit)
                .build();

        return recommendationFacade.executeRecommendation("personalized", context);
    }

    public RecommendationResponse getTrending(UUID tenantId, int limit, int days) {
        int cappedLimit = Math.min(limit > 0 ? limit : 10, maxRecommendationLimit);
        RecommendationContext context = RecommendationContext.builder()
                .tenantId(tenantId)
                .limit(cappedLimit)
                .tenantConfig(Map.of("days", days))
                .build();

        return recommendationFacade.executeRecommendation("trending", context);
    }

    public RecommendationResponse getRecommendationsByStrategy(String strategyName, RecommendationContext context) {
        if (context.getLimit() <= 0) {
            context.setLimit(Math.min(10, maxRecommendationLimit));
        } else {
            context.setLimit(Math.min(context.getLimit(), maxRecommendationLimit));
        }
        return recommendationFacade.executeRecommendation(strategyName, context);
    }
}
