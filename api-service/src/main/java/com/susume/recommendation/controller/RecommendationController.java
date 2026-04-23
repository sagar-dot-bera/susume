package com.susume.recommendation.controller;

import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.service.RecommendationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/**
 * REST controller for recommendation endpoints.
 *
 * Authentication: All endpoints require X-API-KEY header (handled by
 * ApiKeyFilter).
 * Tenant isolation: TenantContext is set by ApiKeyFilter.
 */
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
     *
     * @param externalUserId external user identifier
     * @param limit          number of recommendations (optional, default 10, max
     *                       configured in env)
     * @return RecommendationResponse with personalized or trending recommendations
     */
    @GetMapping("/{externalUserId}")
    public ResponseEntity<RecommendationResponse> getRecommendations(
            @PathVariable String externalUserId,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            UUID tenantId = getTenantIdFromContext();

            if (limit <= 0) {
                return ResponseEntity.badRequest().build();
            }

            RecommendationResponse response = recommendationService.getRecommendations(
                    tenantId, externalUserId, limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting recommendations for user {}: {}", externalUserId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get trending recommendations.
     * Returns items with highest weighted interaction counts in the past N days.
     *
     * @param limit number of recommendations (optional, default 10, max configured
     *              in env)
     * @param days  lookback window in days (optional, default 30)
     * @return RecommendationResponse with trending recommendations
     */
    @GetMapping("/trending")
    public ResponseEntity<RecommendationResponse> getTrending(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int days) {

        try {
            UUID tenantId = getTenantIdFromContext();

            if (limit <= 0 || days <= 0) {
                return ResponseEntity.badRequest().build();
            }

            RecommendationResponse response = recommendationService.getTrending(tenantId, limit, days);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting trending recommendations: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Extract tenant ID from thread-local context set by ApiKeyFilter.
     * Throws exception if tenant not set (indicates missing or invalid API key).
     */
    private UUID getTenantIdFromContext() {
        // Import from TenantContext (created in auth-tenant workflow)
        UUID tenantId = com.susume.recommendation.filter.TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant ID not set in context");
        }
        return tenantId;
    }
}
