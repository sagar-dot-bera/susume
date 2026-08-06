package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.service.RecommendationStrategy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FrequentlyBoughtTogetherStrategy implements RecommendationStrategy {

    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;

    public FrequentlyBoughtTogetherStrategy(InteractionRepository interactionRepository, ItemRepository itemRepository) {
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.FREQUENTLY_BOUGHT_TOGETHER;
    }

    @Override
    public String getName() {
        return "frequently_bought_together";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        long start = System.nanoTime();
        if (context.getExternalItemId() == null) {
            return RecommendationResult.builder()
                    .strategy(getName())
                    .items(Collections.emptyList())
                    .executionTimeMs((System.nanoTime() - start) / 1_000_000)
                    .build();
        }

        List<Interaction> purchases = interactionRepository.findByTenantId(context.getTenantId()).stream()
                .filter(i -> i.getInteractionType() == InteractionType.PURCHASE || i.getInteractionType() == InteractionType.CART)
                .collect(Collectors.toList());

        Map<String, Set<String>> userPurchases = new HashMap<>();
        for (Interaction i : purchases) {
            userPurchases.computeIfAbsent(i.getExternalUserId(), k -> new HashSet<>()).add(i.getExternalItemId());
        }

        Map<String, Integer> coOccurrenceMap = new HashMap<>();
        for (Set<String> items : userPurchases.values()) {
            if (items.contains(context.getExternalItemId())) {
                for (String item : items) {
                    if (!item.equalsIgnoreCase(context.getExternalItemId())) {
                        coOccurrenceMap.merge(item, 1, Integer::sum);
                    }
                }
            }
        }

        List<RecommendationItemResponse> recommendations = coOccurrenceMap.entrySet().stream()
                .map(e -> {
                    Item item = itemRepository.findActiveByTenantIdAndExternalItemId(context.getTenantId(), e.getKey());
                    if (item == null) return null;
                    return RecommendationItemResponse.builder()
                            .externalItemId(e.getKey())
                            .metadata(item.getMetadata())
                            .similarityScore(e.getValue().doubleValue())
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
