package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import com.susume.recommendation.framework.SimilarityService;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.service.RecommendationStrategy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CollaborativeFilteringStrategy implements RecommendationStrategy {

    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;
    private final SimilarityService similarityService = new SimilarityService();

    public CollaborativeFilteringStrategy(InteractionRepository interactionRepository, ItemRepository itemRepository) {
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.COLLABORATIVE_FILTERING;
    }

    @Override
    public String getName() {
        return "collaborative_filtering";
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

        List<Interaction> userInteractions = interactionRepository.findByTenantIdAndExternalUserId(
                context.getTenantId(), context.getExternalUserId().toString());
        Set<String> userItemSet = userInteractions.stream().map(Interaction::getExternalItemId).collect(Collectors.toSet());

        if (userItemSet.isEmpty()) {
            return RecommendationResult.builder()
                    .strategy(getName())
                    .items(Collections.emptyList())
                    .executionTimeMs((System.nanoTime() - start) / 1_000_000)
                    .build();
        }

        List<Interaction> allTenantInteractions = interactionRepository.findByTenantId(context.getTenantId());
        Map<String, Set<String>> userToItemsMap = new HashMap<>();
        for (Interaction i : allTenantInteractions) {
            userToItemsMap.computeIfAbsent(i.getExternalUserId(), k -> new HashSet<>()).add(i.getExternalItemId());
        }

        Map<String, Double> itemScores = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : userToItemsMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(context.getExternalUserId().toString())) continue;

            double jaccardSim = similarityService.computeJaccardSimilarity(userItemSet, entry.getValue());
            if (jaccardSim > 0.0) {
                for (String itemId : entry.getValue()) {
                    if (!userItemSet.contains(itemId)) {
                        itemScores.merge(itemId, jaccardSim, Double::sum);
                    }
                }
            }
        }

        List<RecommendationItemResponse> recommendations = itemScores.entrySet().stream()
                .map(e -> {
                    Item item = itemRepository.findActiveByTenantIdAndExternalItemId(context.getTenantId(), e.getKey());
                    if (item == null) return null;
                    return RecommendationItemResponse.builder()
                            .externalItemId(e.getKey())
                            .metadata(item.getMetadata())
                            .similarityScore(e.getValue())
                            .build();
                })
                .filter(Objects::nonNull)
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
