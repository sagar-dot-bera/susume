package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.util.VectorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for computing personalized and trending recommendations.
 *
 * Algorithm Overview:
 * 1. For a user with interaction history:
 * - Fetch all interactions and their associated item embeddings
 * - Compute a weighted average user vector (weight by interaction type)
 * - Score all active items (not yet interacted with) using cosine similarity
 * - Return top N ranked by score
 *
 * 2. For cold-start (user with no history):
 * - Fall back to trending items (scored by weighted interaction counts)
 */
@Slf4j
@Service
public class RecommendationService {

    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;
    private final int maxRecommendationLimit;

    public RecommendationService(
            InteractionRepository interactionRepository,
            ItemRepository itemRepository,
            @Value("${recommendation.max-limit:50}") int maxRecommendationLimit) {
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
        this.maxRecommendationLimit = maxRecommendationLimit;
    }

    @Cacheable(value = "recommendation", key = "#tenantId" + "#externalUserId")
    public RecommendationResponse getRecommendations(UUID tenantId, String externalUserId, int limit) {
        int cappedLimit = Math.min(limit, maxRecommendationLimit);

        // Step 1: INTERACTION RETRIEVAL
        List<Interaction> interactions = interactionRepository.findByTenantIdAndExternalUserId(tenantId,
                externalUserId);

        if (interactions.isEmpty()) {
            log.debug("No interactions found for user {}, falling back to trending", externalUserId);
            return getTrending(tenantId, limit, 30);
        }

        // Step 2: EMBEDDING FETCH & WEIGHT COLLECTION
        List<float[]> embeddings = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        Set<String> interactedItemIds = new HashSet<>();

        for (Interaction interaction : interactions) {
            interactedItemIds.add(interaction.getExternalItemId());
            Item item = itemRepository.findActiveByTenantIdAndExternalItemId(
                    tenantId, interaction.getExternalItemId());

            if (item != null && item.getEmbedding() != null) {
                embeddings.add(item.getEmbedding());
                weights.add(interaction.getInteractionType().getWeight());
            }
        }

        // If no valid embeddings found, fall back to trending
        if (embeddings.isEmpty()) {
            log.debug("No valid embeddings found for user interactions, falling back to trending");
            return getTrending(tenantId, limit, 30);
        }

        // Step 3: USER VECTOR COMPUTATION
        float[] userVector = VectorUtils.weightedAverage(embeddings, weights);

        // Step 4: CANDIDATE SET
        List<Item> allActiveItems = itemRepository.findByTenantIdAndStatus(
                tenantId, "ACTIVE", PageRequest.of(0, Integer.MAX_VALUE)).getContent();

        List<Item> candidates = allActiveItems.stream()
                .filter(item -> !interactedItemIds.contains(item.getExternalItemId()))
                .collect(Collectors.toList());

        // Step 5: COSINE SIMILARITY RANKING
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
                .limit(cappedLimit)
                .collect(Collectors.toList());

        // Step 6: RETURN TOP N
        return RecommendationResponse.builder()
                .userId(externalUserId)
                .recommendations(scoredItems)
                .strategy("personalized")
                .build();
    }

    /**
     * Get trending recommendations based on weighted interaction counts.
     * Used for cold-start users or when explicitly requested.
     *
     * @param tenantId tenant UUID
     * @param limit    number of recommendations to return (capped at max limit)
     * @param days     lookback window in days for counting interactions
     * @return RecommendationResponse with trending strategy
     */
    public RecommendationResponse getTrending(UUID tenantId, int limit, int days) {
        int cappedLimit = Math.min(limit, maxRecommendationLimit);

        // Calculate timestamp threshold (N days ago)
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        // Query for trending items (weighted by interaction type)
        Pageable pageable = PageRequest.of(0, cappedLimit);
        List<Object[]> trendingResults = interactionRepository.findTrendingItemIds(tenantId, since, pageable);

        // Fetch items and build responses
        List<RecommendationItemResponse> recommendations = new ArrayList<>();

        for (Object[] result : trendingResults) {
            String externalItemId = (String) result[0];
            Number scoreObj = (Number) result[1];
            double score = scoreObj != null ? scoreObj.doubleValue() : 0.0;

            Item item = itemRepository.findActiveByTenantIdAndExternalItemId(tenantId, externalItemId);
            if (item != null) {
                recommendations.add(RecommendationItemResponse.builder()
                        .externalItemId(externalItemId)
                        .metadata(item.getMetadata())
                        .similarityScore(score)
                        .build());
            }
        }

        return RecommendationResponse.builder()
                .userId(null) // Trending doesn't target a specific user
                .recommendations(recommendations)
                .strategy("trending")
                .build();
    }
}
