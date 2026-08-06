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
public class PersonalizedStrategy implements RecommendationStrategy {

    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;

    public PersonalizedStrategy(InteractionRepository interactionRepository, ItemRepository itemRepository) {
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public RecommendationAlgorithm getAlgorithm() {
        return RecommendationAlgorithm.PERSONALIZED;
    }

    @Override
    public String getName() {
        return "personalized";
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        long start = System.nanoTime();
        if (context.getExternalUserId() == null) {
            return RecommendationResult.builder()
                    .strategy(getName())
                    .items(Collections.emptyList())
                    .executionTimeMs(0)
                    .metadata(Map.of("reason", "cold-start"))
                    .build();
        }

        List<Interaction> interactions = interactionRepository.findByTenantIdAndExternalUserId(
                context.getTenantId(), context.getExternalUserId().toString());

        if (interactions.isEmpty()) {
            return RecommendationResult.builder()
                    .strategy(getName())
                    .items(Collections.emptyList())
                    .executionTimeMs(0)
                    .metadata(Map.of("reason", "cold-start"))
                    .build();
        }

        List<float[]> embeddings = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        Set<String> interactedItemIds = new HashSet<>();

        for (Interaction interaction : interactions) {
            interactedItemIds.add(interaction.getExternalItemId());
            Item item = itemRepository.findActiveByTenantIdAndExternalItemId(
                    context.getTenantId(), interaction.getExternalItemId());

            if (item != null && item.getEmbedding() != null) {
                embeddings.add(item.getEmbedding());
                weights.add(interaction.getInteractionType().getWeight());
            }
        }

        if (embeddings.isEmpty()) {
            return RecommendationResult.builder()
                    .strategy(getName())
                    .items(Collections.emptyList())
                    .executionTimeMs((System.nanoTime() - start) / 1_000_000)
                    .metadata(Map.of("reason", "no-valid-embeddings"))
                    .build();
        }

        float[] userVector = VectorUtils.weightedAverage(embeddings, weights);

        List<Item> allActiveItems = itemRepository.findByTenantIdAndStatus(
                context.getTenantId(), "ACTIVE", PageRequest.of(0, Integer.MAX_VALUE)).getContent();

        List<Item> candidates = allActiveItems.stream()
                .filter(item -> !interactedItemIds.contains(item.getExternalItemId()))
                .collect(Collectors.toList());

        List<RecommendationItemResponse> scoredItems = candidates.stream()
                .filter(item -> item.getEmbedding() != null)
                .map(item -> {
                    double score = VectorUtils.cosineSimilarity(userVector, item.getEmbedding());
                    return RecommendationItemResponse.builder()
                            .externalItemId(item.getExternalItemId())
                            .metadata(item.getMetadata())
                            .similarityScore(score)
                            .build();
                })
                .sorted(Comparator.comparingDouble(RecommendationItemResponse::getSimilarityScore).reversed())
                .limit(context.getEffectiveLimit(50))
                .collect(Collectors.toList());

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return RecommendationResult.builder()
                .strategy(getName())
                .items(scoredItems)
                .executionTimeMs(latencyMs)
                .metadata(Map.of("userVectorCalculated", true))
                .build();
    }
}
