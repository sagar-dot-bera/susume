package com.susume.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single recommendation item in a recommendation response.
 * Includes the external item ID, metadata, and similarity score.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationItemResponse {
    private String externalItemId;
    private Object metadata;
    private double similarityScore;
}
