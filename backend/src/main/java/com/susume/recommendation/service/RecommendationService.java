package com.susume.recommendation.service;

import com.susume.recommendation.cache.RecommendationCache;
import com.susume.recommendation.client.RecommendationRankingClient;
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
    private final RecommendationCache recommendationCache;
    private final RecommendationRankingClient rankingClient;
    private final ImpressionLogger impressionLogger;
    private final int maxRecommendationLimit;

    public RecommendationService(
            RecommendationFacade recommendationFacade,
            RecommendationCache recommendationCache,
            RecommendationRankingClient rankingClient,
            ImpressionLogger impressionLogger,
            @Value("${recommendation.max-limit:50}") int maxRecommendationLimit) {
        this.recommendationFacade = recommendationFacade;
        this.recommendationCache = recommendationCache;
        this.rankingClient = rankingClient;
        this.impressionLogger = impressionLogger;
        this.maxRecommendationLimit = maxRecommendationLimit;
    }

    /** Served to an end user: impressions are logged whether or not the result came from cache. */
    public RecommendationResponse getRecommendations(RecommendationRequest request, UUID tenantId) {
        int cappedLimit = capLimit(request.limit());
        RecommendationResponse response = recommendationCache.getPersonalized(
                tenantId, request.externalUserId(), cappedLimit, rankingClient.currentModelVersion(),
                () -> recommendationFacade.executeRecommendation("personalized", RecommendationContext.builder()
                        .tenantId(tenantId)
                        .externalUserId(request.externalUserId())
                        .limit(cappedLimit)
                        .build()));
        impressionLogger.log(tenantId, request.externalUserId(), response);
        return response;
    }

    public RecommendationResponse getTrending(UUID tenantId, int limit, int days) {
        int cappedLimit = capLimit(limit);
        RecommendationResponse response = recommendationCache.getTrending(tenantId, cappedLimit, days,
                () -> recommendationFacade.executeRecommendation("trending", RecommendationContext.builder()
                        .tenantId(tenantId)
                        .limit(cappedLimit)
                        .tenantConfig(Map.of("days", days))
                        .build()));
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

    private int capLimit(int limit) {
        return Math.min(limit > 0 ? limit : 10, maxRecommendationLimit);
    }
}
