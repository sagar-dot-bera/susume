package com.susume.recommendation.dto;

import java.time.Instant;

public interface RecommendationOverTimeProjection {
    Instant getHour();

    Long getCount();
}
