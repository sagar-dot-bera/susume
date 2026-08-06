package com.susume.recommendation.framework;

import com.susume.recommendation.service.RecommendationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RecommendationStrategyResolver {

    private final Map<String, RecommendationStrategy> strategies = new ConcurrentHashMap<>();

    @Autowired
    public RecommendationStrategyResolver(List<RecommendationStrategy> strategyList) {
        for (RecommendationStrategy strategy : strategyList) {
            strategies.put(strategy.getName().toLowerCase(), strategy);
            strategies.put(strategy.getAlgorithm().name().toLowerCase(), strategy);
        }
    }

    public RecommendationStrategy resolve(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return Optional.ofNullable(strategies.get("personalized"))
                    .orElseThrow(() -> new IllegalArgumentException("Default strategy 'personalized' not found"));
        }

        RecommendationStrategy strategy = strategies.get(strategyName.toLowerCase());
        if (strategy != null) {
            return strategy;
        }

        return strategies.values().stream()
                .filter(s -> s.supports(strategyName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No strategy registered for name: " + strategyName));
    }

    public List<RecommendationStrategy> getAllStrategies() {
        return List.copyOf(strategies.values().stream().distinct().toList());
    }
}
