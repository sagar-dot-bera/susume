package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationRequest;
import com.susume.recommendation.dto.RecommendationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Computes (and caches) a user's personalized recommendations. Kept separate from
 * RecommendationService so that service can log impressions on every request, cache hit or not:
 * Spring's cache proxy skips the method body entirely on a hit.
 */
@Service
public class PersonalizedRecommendationProvider {

    private final RecommendationFacade recommendationFacade;
    private final int maxRecommendationLimit;

    public PersonalizedRecommendationProvider(
            RecommendationFacade recommendationFacade,
            @Value("${recommendation.max-limit:50}") int maxRecommendationLimit) {
        this.recommendationFacade = recommendationFacade;
        this.maxRecommendationLimit = maxRecommendationLimit;
    }

    // The "<tenantId>:<userId>:" prefix is what RecommendationCacheEvictor (and interaction-service) evicts.
    @Cacheable(value = "recommendation", key = "#tenantId + ':' + #request.externalUserId() + ':' + #request.limit()")
    public RecommendationResponse get(RecommendationRequest request, UUID tenantId) {
        int cappedLimit = Math.min(request.limit() > 0 ? request.limit() : 10, maxRecommendationLimit);
        RecommendationContext context = RecommendationContext.builder()
                .tenantId(tenantId)
                .externalUserId(request.externalUserId())
                .limit(cappedLimit)
                .build();

        return recommendationFacade.executeRecommendation("personalized", context);
    }
}
