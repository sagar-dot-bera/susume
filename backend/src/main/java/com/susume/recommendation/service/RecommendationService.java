package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationRequest;
import com.susume.recommendation.dto.RecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class RecommendationService {

    private final RecommendationFacade recommendationFacade;
    private final PersonalizedRecommendationProvider personalizedProvider;
    private final ImpressionLogger impressionLogger;
    private final int maxRecommendationLimit;

    public RecommendationService(
            RecommendationFacade recommendationFacade,
            PersonalizedRecommendationProvider personalizedProvider,
            ImpressionLogger impressionLogger,
            @Value("${recommendation.max-limit:50}") int maxRecommendationLimit) {
        this.recommendationFacade = recommendationFacade;
        this.personalizedProvider = personalizedProvider;
        this.impressionLogger = impressionLogger;
        this.maxRecommendationLimit = maxRecommendationLimit;
    }

    /** Served to an end user: impressions are logged whether or not the result came from cache. */
    public RecommendationResponse getRecommendations(RecommendationRequest request, UUID tenantId) {
        RecommendationResponse response = personalizedProvider.get(request, tenantId);
        impressionLogger.log(tenantId, request.externalUserId(), response);
        return response;
    }

    public RecommendationResponse getTrending(UUID tenantId, int limit, int days) {
        int cappedLimit = Math.min(limit > 0 ? limit : 10, maxRecommendationLimit);
        RecommendationContext context = RecommendationContext.builder()
                .tenantId(tenantId)
                .limit(cappedLimit)
                .tenantConfig(Map.of("days", days))
                .build();

        RecommendationResponse response = recommendationFacade.executeRecommendation("trending", context);
        impressionLogger.log(tenantId, null, response);
        return response;
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
