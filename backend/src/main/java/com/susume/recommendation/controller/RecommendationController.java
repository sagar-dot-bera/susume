package com.susume.recommendation.controller;

import com.susume.recommendation.dto.RecommendationRequest;
import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.service.RecommendationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * Get personalized recommendations for a user.
     * Falls back to trending if user has no interaction history.
     * Authenticated via API key (TenantContext)
     *
     * @param request recommendation request with externalUserId and optional limit
     * @return RecommendationResponse with personalized or trending recommendations
     */
    @GetMapping()
    public ResponseEntity<RecommendationResponse> getRecommendations(
            @RequestBody RecommendationRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        RecommendationResponse recommendationResponse = recommendationService.getRecommendations(request, tenantId);
        return ResponseEntity.ok(recommendationResponse);
    }

    /**
     * Get trending recommendations.
     * Returns items with highest weighted interaction counts in the past N days.
     * Authenticated via API key (TenantContext)
     *
     * @param limit number of recommendations (optional, default 10)
     * @param days  lookback window in days (optional, default 30)
     * @return RecommendationResponse with trending recommendations
     */
    @GetMapping("/trending")
    public ResponseEntity<RecommendationResponse> getTrending(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int days) {

        UUID tenantId = TenantContext.getTenantId();

        if (limit <= 0 || days <= 0) {
            return ResponseEntity.badRequest().build();
        }

        RecommendationResponse response = recommendationService.getTrending(tenantId, limit, days);
        return ResponseEntity.ok(response);
    }
}
