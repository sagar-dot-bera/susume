package com.susume.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

/**
 * Response for both personalized and trending recommendations.
 * The 'strategy' field indicates which algorithm was used.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResponse {
    private UUID userId;
    private List<RecommendationItemResponse> recommendations;
    private String strategy; // "personalized" or "trending"
}
