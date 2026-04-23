package com.susume.recommendation.controller;

import com.susume.recommendation.dto.TenantRegistrationRequest;
import com.susume.recommendation.dto.TenantRegistrationResponse;
import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.service.TenantService;
import com.susume.recommendation.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/tenants")
@Slf4j
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Register a new tenant.
     * POST /api/v1/tenants/register
     * Body: { "name": "...", "contactEmail": "..." }
     * Response: { "tenantId": "...", "apiKey": "...", "name": "...", "createdAt":
     * "..." }
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody TenantRegistrationRequest request) {
        try {
            com.susume.recommendation.service.TenantRegistrationData registrationData = tenantService
                    .register(request.name, request.contactEmail);

            TenantRegistrationResponse response = new TenantRegistrationResponse(
                    registrationData.tenant.getId(),
                    registrationData.rawApiKey,
                    registrationData.tenant.getName(),
                    registrationData.tenant.getCreatedAt());

            log.info("Tenant registered successfully: {}", registrationData.tenant.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Tenant registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new com.susume.recommendation.dto.ErrorResponse("CONFLICT", e.getMessage()));
        } catch (Exception e) {
            log.error("Error registering tenant", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new com.susume.recommendation.dto.ErrorResponse("INTERNAL_ERROR",
                            "Error registering tenant"));
        }
    }
}
