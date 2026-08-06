package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.service.RecommendationStrategy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class TrendingStrategy implements RecommendationStrategy {

    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;

    public TrendingStrategy(InteractionRepository interactionRepository, ItemRepository itemRepository) {
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.TRENDING;
    }

    @Override
    public String getName() {
        return "trending";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        long start = System.nanoTime();
        int cappedLimit = context.getEffectiveLimit(10);
        int days = 30;

        if (context.getTenantConfig() != null && context.getTenantConfig().containsKey("days")) {
            days = ((Number) context.getTenantConfig().get("days")).intValue();
        }

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        Pageable pageable = PageRequest.of(0, cappedLimit);
        List<Object[]> trendingResults = interactionRepository.findTrendingItemIds(context.getTenantId(), since, pageable);

        List<RecommendationItemResponse> recommendations = new ArrayList<>();
        for (Object[] result : trendingResults) {
            String externalItemId = (String) result[0];
            Number scoreObj = (Number) result[1];
            double score = scoreObj != null ? scoreObj.doubleValue() : 0.0;

            Item item = itemRepository.findActiveByTenantIdAndExternalItemId(context.getTenantId(), externalItemId);
            if (item != null) {
                recommendations.add(RecommendationItemResponse.builder()
                        .externalItemId(externalItemId)
                        .metadata(item.getMetadata())
                        .similarityScore(score)
                        .build());
            }
        }

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return RecommendationResult.builder()
                .strategy(getName())
                .items(recommendations)
                .executionTimeMs(latencyMs)
                .metadata(Map.of("daysLookback", days))
                .build();
    }
}
