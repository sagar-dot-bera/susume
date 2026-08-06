package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.service.RecommendationStrategy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RandomDiscoveryStrategy implements RecommendationStrategy {

    private final ItemRepository itemRepository;

    public RandomDiscoveryStrategy(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.RANDOM_DISCOVERY;
    }

    @Override
    public String getName() {
        return "random_discovery";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        long start = System.nanoTime();
        List<Item> candidates = new ArrayList<>(itemRepository.findByTenantIdAndStatus(
                context.getTenantId(), "ACTIVE", PageRequest.of(0, Integer.MAX_VALUE)).getContent());

        Collections.shuffle(candidates);

        List<RecommendationItemResponse> recommendations = candidates.stream()
                .limit(context.getEffectiveLimit(10))
                .map(item -> RecommendationItemResponse.builder()
                        .externalItemId(item.getExternalItemId())
                        .metadata(item.getMetadata())
                        .similarityScore(Math.random())
                        .build())
                .collect(Collectors.toList());

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return RecommendationResult.builder()
                .strategy(getName())
                .items(recommendations)
                .executionTimeMs(latencyMs)
                .build();
    }
}
