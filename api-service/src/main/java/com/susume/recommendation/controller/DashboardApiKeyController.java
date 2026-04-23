package com.susume.recommendation.controller;

import com.susume.recommendation.dto.ApiKeyResponse;
import com.susume.recommendation.dto.ApiKeyRegenerateResponse;
import com.susume.recommendation.dto.ErrorResponse;
import com.susume.recommendation.filter.JwtContext;
import com.susume.recommendation.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard/api-key")
@Slf4j
public class DashboardApiKeyController {

    private final TenantService tenantService;

    public DashboardApiKeyController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Get masked API key.
     * GET /api/v1/dashboard/api-key
     * Response: { "maskedKey": "********abcd1234" }
     */
    @GetMapping
    public ResponseEntity<?> getApiKey() {
        try {
            UUID tenantId = JwtContext.getTenantId();
            String role = JwtContext.getRole();

            // Verify ADMIN role
            if (!"ADMIN".equals(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("FORBIDDEN", "Insufficient permissions"));
            }

            // This is a simplified response - in a real application,
            // you'd fetch the actual API key hash and mask it
            // For now, just return a placeholder
            String maskedKey = "****"; // Placeholder

            log.info("API key retrieved for tenant: {}", tenantId);
            return ResponseEntity.ok(new ApiKeyResponse(maskedKey));

        } catch (Exception e) {
            log.error("Error retrieving API key", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error retrieving API key"));
        }
    }

    /**
     * Regenerate API key.
     * POST /api/v1/dashboard/api-key/regenerate
     * Response: { "apiKey": "..." }
     */
    @PostMapping("/regenerate")
    public ResponseEntity<?> regenerateApiKey() {
        try {
            UUID tenantId = JwtContext.getTenantId();
            String role = JwtContext.getRole();

            // Verify ADMIN role
            if (!"ADMIN".equals(role)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("FORBIDDEN", "Insufficient permissions"));
            }

            String newApiKey = tenantService.regenerateApiKey(tenantId);

            log.info("API key regenerated for tenant: {}", tenantId);
            return ResponseEntity.ok(new ApiKeyRegenerateResponse(newApiKey));

        } catch (IllegalArgumentException e) {
            log.warn("API key regeneration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", "Tenant not found"));
        } catch (Exception e) {
            log.error("Error regenerating API key", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error regenerating API key"));
        }
    }
}
