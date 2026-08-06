package com.susume.recommendation.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.susume.recommendation.dto.DashboardStatsResponse;
import com.susume.recommendation.dto.RecommendationOverTimeProjection;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.repository.RecommendationResultRepository;
import com.susume.recommendation.repository.RecommendationSessionRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StatesService {
    private final ItemRepository itemRepository;
    private final InteractionRepository interactionRepository;
    private final RecommendationSessionRepository recommendationSessionRepository;
    private final RecommendationResultRepository recommendationResultRepository;

    public StatesService(
            ItemRepository itemRepository,
            InteractionRepository interactionRepository,
            RecommendationSessionRepository recommendationSessionRepository,
            RecommendationResultRepository recommendationResultRepository) {
        this.itemRepository = itemRepository;
        this.interactionRepository = interactionRepository;
        this.recommendationSessionRepository = recommendationSessionRepository;
        this.recommendationResultRepository = recommendationResultRepository;
    }

    public DashboardStatsResponse getDashboardStats(UUID tenantId) {
        int itemCount = itemRepository.countByTenantId(tenantId);
        Map<String, Long> typeBreakdown = new HashMap<>();
        typeBreakdown.put("VIEW", 0L);
        typeBreakdown.put("CLICK", 0L);
        typeBreakdown.put("LIKE", 0L);
        typeBreakdown.put("PURCHASE", 0L);

        List<Interaction> interactions = interactionRepository.findByTenantId(tenantId);
        for (Interaction interaction : interactions) {
            String type = interaction.getInteractionType().name();
            typeBreakdown.put(type, typeBreakdown.getOrDefault(type, 0L) + 1);
        }

        long totalInteractions = interactions.size();
        int avgLatency = recommendationSessionRepository.findAverageLatencyByTenantId(tenantId);

        int apiKeyCount = 0; // Placeholder, implement if API key tracking is added

        Map<String, Long> hitsOverTime = new HashMap<>();

        List<RecommendationOverTimeProjection> recommendationCounts = recommendationSessionRepository
                .getRecommendationCountsLast7Hours(tenantId);

        List<UUID> sessionIds = recommendationSessionRepository.findDistinctByTenantId(tenantId);
        int totalRecs = 0;
        for (UUID sessionId : sessionIds) {
            long count = recommendationResultRepository.countBySessionId(sessionId);
            totalRecs += count;
        }

        for (RecommendationOverTimeProjection projection : recommendationCounts) {

            hitsOverTime.put("hour" + projection.getHour().toString(), projection.getCount());

        }

        DashboardStatsResponse response = new DashboardStatsResponse(itemCount, totalRecs, totalInteractions,
                avgLatency, apiKeyCount,
                hitsOverTime,
                typeBreakdown);

        log.info("Dashboard stats for tenant {}: {}", tenantId, response);
        return response;
    }

}
