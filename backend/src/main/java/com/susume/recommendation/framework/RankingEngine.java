package com.susume.recommendation.framework;

import com.susume.recommendation.dto.RecommendationItemResponse;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RankingEngine {

    public List<RecommendationItemResponse> rankAndCap(List<RecommendationItemResponse> items, int limit) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        // Deduplicate keeping highest similarity score
        Map<String, RecommendationItemResponse> deduplicated = new LinkedHashMap<>();
        for (RecommendationItemResponse item : items) {
            String itemId = item.getExternalItemId();
            if (!deduplicated.containsKey(itemId) ||
                    item.getSimilarityScore() > deduplicated.get(itemId).getSimilarityScore()) {
                deduplicated.put(itemId, item);
            }
        }

        return deduplicated.values().stream()
                .sorted(Comparator.comparingDouble(RecommendationItemResponse::getSimilarityScore).reversed())
                .limit(limit > 0 ? limit : Integer.MAX_VALUE)
                .collect(Collectors.toList());
    }

    public List<RecommendationItemResponse> normalizeScores(List<RecommendationItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        double max = items.stream().mapToDouble(RecommendationItemResponse::getSimilarityScore).max().orElse(1.0);
        double min = items.stream().mapToDouble(RecommendationItemResponse::getSimilarityScore).min().orElse(0.0);

        double range = max - min;
        if (range <= 0.00001) {
            return items;
        }

        return items.stream().map(item -> RecommendationItemResponse.builder()
                .externalItemId(item.getExternalItemId())
                .metadata(item.getMetadata())
                .similarityScore((item.getSimilarityScore() - min) / range)
                .build()).collect(Collectors.toList());
    }
}
