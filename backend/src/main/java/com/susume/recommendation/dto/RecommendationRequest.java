package com.susume.recommendation.dto;

import java.util.UUID;

public record RecommendationRequest(
                UUID externalUserId, int limit) {

}
