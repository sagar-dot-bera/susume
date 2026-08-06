package com.susume.recommendation.controller;

import com.susume.recommendation.dto.ApiKeyDto;
import com.susume.recommendation.dto.CreateApiKeyResponse;
import com.susume.recommendation.dto.ErrorResponse;
import com.susume.recommendation.filter.JwtContext;
import com.susume.recommendation.service.ApiKeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@Slf4j
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * Create a new API key for the authenticated tenant.
     * POST /api/v1/api-keys
     */
    @PostMapping
    public ResponseEntity<?> createApiKey() {
        try {
            UUID tenantId = JwtContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Tenant context missing"));
            }

            CreateApiKeyResponse response = apiKeyService.createApiKey(tenantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error creating API key", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to create API key"));
        }
    }

    /**
     * Get all API keys for the authenticated tenant.
     * GET /api/v1/api-keys
     */
    @GetMapping
    public ResponseEntity<?> getApiKeys() {
        try {
            UUID tenantId = JwtContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Tenant context missing"));
            }

            List<ApiKeyDto> keys = apiKeyService.getApiKeysByTenant(tenantId);
            return ResponseEntity.ok(keys);

        } catch (Exception e) {
            log.error("Error fetching API keys", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to fetch API keys"));
        }
    }

    /**
     * Get specific API key by ID for the authenticated tenant.
     * GET /api/v1/api-keys/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getApiKeyById(@PathVariable UUID id) {
        try {
            UUID tenantId = JwtContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Tenant context missing"));
            }

            Optional<ApiKeyDto> apiKeyOpt = apiKeyService.getApiKeyById(id, tenantId);
            if (apiKeyOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "API key not found"));
            }

            return ResponseEntity.ok(apiKeyOpt.get());

        } catch (Exception e) {
            log.error("Error fetching API key {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to fetch API key"));
        }
    }

    /**
     * Delete/revoke API key by ID for the authenticated tenant.
     * DELETE /api/v1/api-keys/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteApiKey(@PathVariable UUID id) {
        try {
            UUID tenantId = JwtContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Tenant context missing"));
            }

            boolean deleted = apiKeyService.deleteApiKey(id, tenantId);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "API key not found"));
            }

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Error deleting API key {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Failed to delete API key"));
        }
    }
}
