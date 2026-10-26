package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.entity.RecommendationImpression;
import com.susume.recommendation.repository.RecommendationImpressionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records which items were shown to which user, at which position, by which model: the
 * exposure log that online evaluation and future training read. Called for every response
 * served to an end user, including ones served from cache.
 */
@Slf4j
@Service
public class ImpressionLogger {

    private final RecommendationImpressionRepository impressionRepository;

    public ImpressionLogger(RecommendationImpressionRepository impressionRepository) {
        this.impressionRepository = impressionRepository;
    }

    public void log(UUID tenantId, UUID externalUserId, RecommendationResponse response) {
        List<RecommendationItemResponse> items = response.getRecommendations();
        if (items == null || items.isEmpty()) {
            return;
        }
        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        List<RecommendationImpression> impressions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            RecommendationItemResponse item = items.get(i);
            impressions.add(RecommendationImpression.builder()
                    .requestId(requestId)
                    .tenantId(tenantId)
                    .userId(externalUserId != null ? externalUserId.toString() : null)
                    .itemId(item.getExternalItemId())
                    .position(i + 1)
                    .timestamp(now)
                    .modelVersion(response.getModelVersion())
                    .strategyScores(Map.of("score", item.getSimilarityScore()))
                    .build());
        }

        try {
            impressionRepository.saveAll(impressions);
        } catch (Exception e) {
            log.warn("Failed to persist recommendation impressions: {}", e.getMessage());
        }
    }
}
