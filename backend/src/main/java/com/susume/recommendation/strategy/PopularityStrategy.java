package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.entity.RecommendationAlgorithm;
import com.susume.recommendation.framework.ScoreCalculator;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.service.RecommendationStrategy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class PopularityStrategy implements RecommendationStrategy {

    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;
    private final ScoreCalculator scoreCalculator = new ScoreCalculator();

    public PopularityStrategy(InteractionRepository interactionRepository, ItemRepository itemRepository) {
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.POPULARITY;
    }

    @Override
    public String getName() {
        return "popularity";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        long start = System.nanoTime();
        List<Interaction> interactions = interactionRepository.findByTenantId(context.getTenantId());

        Map<String, Double> itemScores = new HashMap<>();
        for (Interaction interaction : interactions) {
            int weight = scoreCalculator.getInteractionWeight(interaction.getInteractionType(), null);
            itemScores.merge(interaction.getExternalItemId(), (double) weight, Double::sum);
        }

        List<RecommendationItemResponse> recommendations = itemScores.entrySet().stream()
                .map(entry -> {
                    Item item = itemRepository.findActiveByTenantIdAndExternalItemId(context.getTenantId(), entry.getKey());
                    if (item == null) return null;
                    return RecommendationItemResponse.builder()
                            .externalItemId(entry.getKey())
                            .metadata(item.getMetadata())
                            .similarityScore(entry.getValue())
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
                .metadata(Map.of("totalInteractionsScored", interactions.size()))
                .build();
    }
}
