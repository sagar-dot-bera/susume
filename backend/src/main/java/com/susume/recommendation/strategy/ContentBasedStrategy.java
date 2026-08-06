package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.service.RecommendationStrategy;
import com.susume.recommendation.util.VectorUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ContentBasedStrategy implements RecommendationStrategy {

    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;

    public ContentBasedStrategy(InteractionRepository interactionRepository, ItemRepository itemRepository) {
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.CONTENT_BASED;
    }

    @Override
    public String getName() {
        return "content_based";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        long start = System.nanoTime();
        if (context.getExternalUserId() == null) {
            return RecommendationResult.builder()
                    .strategy(getName())
                    .items(Collections.emptyList())
                    .executionTimeMs((System.nanoTime() - start) / 1_000_000)
                    .build();
        }

        List<Interaction> interactions = interactionRepository.findByTenantIdAndExternalUserId(
                context.getTenantId(), context.getExternalUserId().toString());

        List<float[]> userEmbeddings = new ArrayList<>();
        Set<String> interactedIds = new HashSet<>();
        for (Interaction i : interactions) {
            interactedIds.add(i.getExternalItemId());
            Item item = itemRepository.findActiveByTenantIdAndExternalItemId(context.getTenantId(), i.getExternalItemId());
            if (item != null && item.getEmbedding() != null) {
                userEmbeddings.add(item.getEmbedding());
            }
        }

        if (userEmbeddings.isEmpty()) {
            return RecommendationResult.builder()
                    .strategy(getName())
                    .items(Collections.emptyList())
                    .executionTimeMs((System.nanoTime() - start) / 1_000_000)
                    .build();
        }

        float[] avgVector = VectorUtils.average(userEmbeddings);
        List<Item> candidates = itemRepository.findByTenantIdAndStatus(
                context.getTenantId(), "ACTIVE", PageRequest.of(0, Integer.MAX_VALUE)).getContent();

        List<RecommendationItemResponse> recommendations = candidates.stream()
                .filter(item -> !interactedIds.contains(item.getExternalItemId()))
                .filter(item -> item.getEmbedding() != null)
                .map(item -> RecommendationItemResponse.builder()
                        .externalItemId(item.getExternalItemId())
                        .metadata(item.getMetadata())
                        .similarityScore(VectorUtils.cosineSimilarity(avgVector, item.getEmbedding()))
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
