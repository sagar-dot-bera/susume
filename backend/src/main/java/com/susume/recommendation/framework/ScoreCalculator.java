package com.susume.recommendation.framework;

import com.susume.recommendation.entity.InteractionType;
import java.util.Map;

public class ScoreCalculator {

    private static final Map<InteractionType, Integer> DEFAULT_WEIGHTS = Map.of(
            InteractionType.VIEW, 1,
            InteractionType.CLICK, 2,
            InteractionType.LIKE, 5,
            InteractionType.SHARE, 4,
            InteractionType.CART, 7,
            InteractionType.PURCHASE, 10
    );

    public int getInteractionWeight(InteractionType type, Map<String, Integer> customWeights) {
        if (customWeights != null && type != null && customWeights.containsKey(type.name())) {
            return customWeights.get(type.name());
        }
        return DEFAULT_WEIGHTS.getOrDefault(type, 1);
    }
}
