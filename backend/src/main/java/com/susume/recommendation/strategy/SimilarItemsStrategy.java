package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.service.RecommendationStrategy;
import com.susume.recommendation.util.VectorUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SimilarItemsStrategy implements RecommendationStrategy {

    private final ItemRepository itemRepository;

    public SimilarItemsStrategy(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.SIMILAR_ITEMS;
    }

    @Override
    public String getName() {
        return "similar_items";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        long start = System.nanoTime();
        if (context.getExternalItemId() == null) {
            return RecommendationResult.builder()
                    .strategy(getName())
                    .items(Collections.emptyList())
                    .executionTimeMs((System.nanoTime() - start) / 1_000_000)
                    .metadata(Map.of("reason", "missing-target-item"))
                    .build();
        }

        Item targetItem = itemRepository.findActiveByTenantIdAndExternalItemId(context.getTenantId(), context.getExternalItemId());
        if (targetItem == null || targetItem.getEmbedding() == null) {
            return RecommendationResult.builder()
                    .strategy(getName())
                    .items(Collections.emptyList())
                    .executionTimeMs((System.nanoTime() - start) / 1_000_000)
                    .metadata(Map.of("reason", "target-item-not-found-or-no-embedding"))
                    .build();
        }

        float[] targetVector = targetItem.getEmbedding();
        List<Item> candidates = itemRepository.findByTenantIdAndStatus(
                context.getTenantId(), "ACTIVE", PageRequest.of(0, Integer.MAX_VALUE)).getContent();

        List<RecommendationItemResponse> recommendations = candidates.stream()
                .filter(item -> !item.getExternalItemId().equalsIgnoreCase(context.getExternalItemId()))
                .filter(item -> item.getEmbedding() != null)
                .map(item -> RecommendationItemResponse.builder()
                        .externalItemId(item.getExternalItemId())
                        .metadata(item.getMetadata())
                        .similarityScore(VectorUtils.cosineSimilarity(targetVector, item.getEmbedding()))
                        .build())
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
