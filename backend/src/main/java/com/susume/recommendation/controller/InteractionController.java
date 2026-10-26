package com.susume.recommendation.controller;

import com.susume.recommendation.dto.InteractionExportResponse;
import com.susume.recommendation.dto.InteractionHistoryResponse;
import com.susume.recommendation.dto.RecordInteractionRequest;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.service.InteractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/interactions")
@Slf4j
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    /**
     * Record a new interaction.
     * POST /api/v1/interactions
     * Authenticated via API key (TenantContext)
     */
    @PostMapping
    public ResponseEntity<Void> recordInteraction(@Valid @RequestBody RecordInteractionRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        interactionService.recordInteraction(tenantId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieve a user's interaction history, newest first, with pagination and filters.
     * GET /api/v1/interactions/history?externalUserId=&interactionType=&since=&page=&limit=
     * Authenticated via API key (TenantContext); the tenant always comes from the key.
     */
    @GetMapping({"history", "history/"})
    public ResponseEntity<InteractionHistoryResponse> getUserInteractionHistory(
            @RequestParam String externalUserId,
            @RequestParam(required = false) String interactionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {

        UUID tenantId = TenantContext.getTenantId();
        Page<Interaction> result = interactionService.getUserInteractions(
                tenantId, externalUserId, interactionType, since, page, limit);
        var data = result.getContent()
                .stream()
                .map(interactionService::toHistoryItemResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new InteractionHistoryResponse(
                data, page, limit, result.getTotalElements(), result.getTotalPages()));
    }

    /**
     * Bulk-export all interactions for the authenticated tenant, paginated.
     * GET /api/v1/interactions/export
     * Internal endpoint consumed by the ML recommendation-service offline
     * training pipeline; kept in the Spring Boot service so all database
     * access for interactions lives here rather than in the ML service.
     * Authenticated via API key (TenantContext)
     */
    @GetMapping("export")
    public ResponseEntity<InteractionExportResponse> exportInteractions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "500") int limit) {

        UUID tenantId = TenantContext.getTenantId();
        Page<Interaction> result = interactionService.exportInteractions(tenantId, page, limit);
        var data = result.getContent()
                .stream()
                .map(interactionService::toExportItemResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new InteractionExportResponse(
                data, page, limit, result.getTotalElements(), result.getTotalPages()));
    }

}
