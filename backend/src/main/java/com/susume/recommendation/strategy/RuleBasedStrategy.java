package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import com.susume.recommendation.framework.RecommendationFilter;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.service.RecommendationStrategy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RuleBasedStrategy implements RecommendationStrategy {

    private final ItemRepository itemRepository;
    private final RecommendationFilter filter = new RecommendationFilter();

    public RuleBasedStrategy(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.RULE_BASED;
    }

    @Override
    public String getName() {
        return "rule_based";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        long start = System.nanoTime();
        List<Item> candidates = itemRepository.findByTenantIdAndStatus(
                context.getTenantId(), "ACTIVE", PageRequest.of(0, Integer.MAX_VALUE)).getContent();

        List<Item> filtered = filter.applyCustomFilters(candidates, context.getFilters());

        List<RecommendationItemResponse> recommendations = filtered.stream()
                .map(item -> {
                    double score = 1.0;
                    if (item.getMetadata() != null && Boolean.TRUE.equals(item.getMetadata().get("isBoosted"))) {
                        score += 0.5;
                    }
                    return RecommendationItemResponse.builder()
                            .externalItemId(item.getExternalItemId())
                            .metadata(item.getMetadata())
                            .similarityScore(score)
                            .build();
                })
                .sorted(Comparator.comparingDouble(RecommendationItemResponse::getSimilarityScore).reversed())
                .limit(context.getEffectiveLimit(10))
                .collect(Collectors.toList());

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return RecommendationResult.builder()
                .strategy(getName())
                .items(recommendations)
                .executionTimeMs(latencyMs)
                .build();
    }
}
