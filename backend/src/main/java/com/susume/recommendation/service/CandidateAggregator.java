package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationCandidate;
import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationItemResponse;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CandidateAggregator {

    public static final List<String> ALL_STRATEGIES = List.of(
            "contentBased",
            "collaborativeFiltering",
            "frequentlyBoughtTogether",
            "hybrid",
            "personalized",
            "popularity",
            "randomDiscovery",
            "ruleBased",
            "similarItems",
            "trending"
    );

    private final RecommendationStrategyResolver strategyResolver;
    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;

    public CandidateAggregator(
            RecommendationStrategyResolver strategyResolver,
            InteractionRepository interactionRepository,
            ItemRepository itemRepository) {
        this.strategyResolver = strategyResolver;
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
    }

    public List<RecommendationCandidate> aggregateCandidates(RecommendationContext context) {
        int candidatePoolSize = context.getEffectiveCandidatePoolSize(100);
        RecommendationContext candidateContext = RecommendationContext.builder()
                .tenantId(context.getTenantId())
                .externalUserId(context.getExternalUserId())
                .externalItemId(context.getExternalItemId())
                .limit(candidatePoolSize)
                .candidatePoolSize(candidatePoolSize)
                .filters(context.getFilters())
                .tenantConfig(context.getTenantConfig())
                .stateData(context.getStateData())
                .surface(context.getSurface())
                .hour(context.getHour())
                .dayOfWeek(context.getDayOfWeek())
                .build();

        List<RecommendationStrategy> strategies = strategyResolver.getAllStrategies();
        Map<String, Map<String, Double>> candidateScores = new LinkedHashMap<>();

        for (RecommendationStrategy strategy : strategies) {
            try {
                RecommendationResult result = strategy.recommend(candidateContext);
                if (result != null && result.getItems() != null) {
                    String canonicalName = toCanonicalStrategyName(strategy.getName());
                    for (RecommendationItemResponse item : result.getItems()) {
                        candidateScores
                                .computeIfAbsent(item.getExternalItemId(), k -> new HashMap<>())
                                .put(canonicalName, item.getSimilarityScore());
                    }
                }
            } catch (Exception e) {
                log.warn("Strategy '{}' failed during candidate generation: {}", strategy.getName(), e.getMessage());
            }
        }

        // Fetch user interactions for user-item features
        List<Interaction> userInteractions = Collections.emptyList();
        if (context.getExternalUserId() != null) {
            userInteractions = interactionRepository.findByTenantIdAndExternalUserId(
                    context.getTenantId(), context.getExternalUserId().toString());
        }

        Instant now = Instant.now();
        List<RecommendationCandidate> candidates = new ArrayList<>();

        for (Map.Entry<String, Map<String, Double>> entry : candidateScores.entrySet()) {
            String itemId = entry.getKey();
            Map<String, Double> rawScores = entry.getValue();

            Map<String, Double> strategyScores = new HashMap<>();
            for (String stratKey : ALL_STRATEGIES) {
                strategyScores.put(stratKey, rawScores.getOrDefault(stratKey, 0.0));
            }

            Item item = itemRepository.findActiveByTenantIdAndExternalItemId(context.getTenantId(), itemId);
            Map<String, Object> itemFeatures = buildItemFeatures(item, now);

            Map<String, Object> userItemFeatures = buildUserItemFeatures(userInteractions, itemId, now);

            candidates.add(RecommendationCandidate.builder()
                    .itemId(itemId)
                    .strategyScores(strategyScores)
                    .itemFeatures(itemFeatures)
                    .userItemFeatures(userItemFeatures)
                    .build());
        }

        return candidates;
    }

    public Map<String, Object> buildUserFeatures(UUID tenantId, UUID externalUserId) {
        Map<String, Object> userFeatures = new HashMap<>();
        if (externalUserId == null) {
            userFeatures.put("totalViews", 0);
            userFeatures.put("totalClicks", 0);
            userFeatures.put("totalLikes", 0);
            userFeatures.put("totalPurchases", 0);
            userFeatures.put("totalInteractions", 0);
            userFeatures.put("interactionsLast24Hours", 0);
            userFeatures.put("interactionsLast7Days", 0);
            userFeatures.put("interactionsLast30Days", 0);
            return userFeatures;
        }

        List<Interaction> interactions = interactionRepository.findByTenantIdAndExternalUserId(tenantId, externalUserId.toString());
        Instant now = Instant.now();
        Instant h24 = now.minus(Duration.ofHours(24));
        Instant d7 = now.minus(Duration.ofDays(7));
        Instant d30 = now.minus(Duration.ofDays(30));

        int views = 0, clicks = 0, likes = 0, purchases = 0;
        int last24h = 0, last7d = 0, last30d = 0;

        for (Interaction i : interactions) {
            if (i.getInteractionType() == InteractionType.VIEW) views++;
            else if (i.getInteractionType() == InteractionType.CLICK) clicks++;
            else if (i.getInteractionType() == InteractionType.LIKE) likes++;
            else if (i.getInteractionType() == InteractionType.PURCHASE) purchases++;

            Instant ts = i.getTimestamp();
            if (ts != null) {
                if (ts.isAfter(h24)) last24h++;
                if (ts.isAfter(d7)) last7d++;
                if (ts.isAfter(d30)) last30d++;
            }
        }

        userFeatures.put("totalViews", views);
        userFeatures.put("totalClicks", clicks);
        userFeatures.put("totalLikes", likes);
        userFeatures.put("totalPurchases", purchases);
        userFeatures.put("totalInteractions", interactions.size());
        userFeatures.put("interactionsLast24Hours", last24h);
        userFeatures.put("interactionsLast7Days", last7d);
        userFeatures.put("interactionsLast30Days", last30d);

        return userFeatures;
    }

    private Map<String, Object> buildItemFeatures(Item item, Instant now) {
        Map<String, Object> features = new HashMap<>();
        if (item == null) {
            features.put("itemPopularity", 0.0);
            features.put("recentViews", 0);
            features.put("recentClicks", 0);
            features.put("recentLikes", 0);
            features.put("recentPurchases", 0);
            features.put("itemAge", 0.0);
            return features;
        }

        long itemAgeDays = item.getCreatedAt() != null ? Duration.between(item.getCreatedAt(), now).toDays() : 0;
        features.put("itemPopularity", 0.0);
        features.put("recentViews", 0);
        features.put("recentClicks", 0);
        features.put("recentLikes", 0);
        features.put("recentPurchases", 0);
        features.put("itemAge", (double) itemAgeDays);
        return features;
    }

    private Map<String, Object> buildUserItemFeatures(List<Interaction> userInteractions, String itemId, Instant now) {
        Map<String, Object> features = new HashMap<>();
        int views = 0, clicks = 0, likes = 0, purchases = 0;
        Instant lastTs = null;

        for (Interaction i : userInteractions) {
            if (itemId.equals(i.getExternalItemId())) {
                if (i.getInteractionType() == InteractionType.VIEW) views++;
                else if (i.getInteractionType() == InteractionType.CLICK) clicks++;
                else if (i.getInteractionType() == InteractionType.LIKE) likes++;
                else if (i.getInteractionType() == InteractionType.PURCHASE) purchases++;

                if (i.getTimestamp() != null) {
                    if (lastTs == null || i.getTimestamp().isAfter(lastTs)) {
                        lastTs = i.getTimestamp();
                    }
                }
            }
        }

        double timeSinceLast = lastTs != null ? Duration.between(lastTs, now).toSeconds() / 3600.0 : -1.0;
        features.put("previousViews", views);
        features.put("previousClicks", clicks);
        features.put("previousLikes", likes);
        features.put("previousPurchases", purchases);
        features.put("timeSinceLastInteraction", timeSinceLast);
        return features;
    }

    private String toCanonicalStrategyName(String rawName) {
        if (rawName == null) return "unknown";
        String clean = rawName.replace("_", "").replace("-", "").toLowerCase();
        return switch (clean) {
            case "contentbased" -> "contentBased";
            case "collaborativefiltering" -> "collaborativeFiltering";
            case "frequentlyboughttogether" -> "frequentlyBoughtTogether";
            case "hybrid" -> "hybrid";
            case "personalized" -> "personalized";
            case "popularity" -> "popularity";
            case "randomdiscovery" -> "randomDiscovery";
            case "rulebased" -> "ruleBased";
            case "similaritems" -> "similarItems";
            case "trending" -> "trending";
            default -> rawName;
        };
    }
}
