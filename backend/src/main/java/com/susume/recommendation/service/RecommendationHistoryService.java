package com.susume.recommendation.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.entity.RecommendationResult;
import com.susume.recommendation.entity.RecommendationSession;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.repository.RecommendationResultRepository;
import com.susume.recommendation.repository.RecommendationSessionRepository;

@Service
public class RecommendationHistoryService {
    private final RecommendationSessionRepository recommendationSessionRepository;
    private final RecommendationResultRepository recommendationResultRepository;
    private final ItemRepository itemRepository;

    public RecommendationHistoryService(
            RecommendationSessionRepository recommendationSessionRepository,
            RecommendationResultRepository recommendationResultRepository,
            ItemRepository itemRepository) {
        this.recommendationSessionRepository = recommendationSessionRepository;
        this.recommendationResultRepository = recommendationResultRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * Save a recommendation session and its results.
     *
     * @param externalUserId external user identifier (may be null for trending)
     * @param tenantId       tenant UUID
     * @param strategy       algorithm name (e.g. "personalized", "trending")
     * @param latency        latency in milliseconds
     * @param recommendations list of recommended items
     */
    public void saveRecommendationHistory(String externalUserId, UUID tenantId, String strategy, int latency,
            List<RecommendationItemResponse> recommendations) {
        // Create and save RecommendationSession
        RecommendationSession session = RecommendationSession.builder()
                .tenantId(tenantId)
                .algorithm(strategy)
                .latencyMs(latency)
                .generatedAt(Instant.now())
                .build();
        recommendationSessionRepository.save(session);

        // Create and save RecommendationResult for each recommended item
        int rank = 1;
        for (RecommendationItemResponse item : recommendations) {
            // Look up actual item UUID by externalItemId to avoid UUID.fromString failure on non-UUID IDs
            var dbItem = itemRepository.findActiveByTenantIdAndExternalItemId(tenantId, item.getExternalItemId());
            if (dbItem == null) {
                rank++;
                continue;
            }
            RecommendationResult result = RecommendationResult.builder()
                    .session(session)
                    .itemId(dbItem.getId())
                    .score(item.getSimilarityScore())
                    .rank(rank++)
                    .build();
            recommendationResultRepository.save(result);
        }
    }
}