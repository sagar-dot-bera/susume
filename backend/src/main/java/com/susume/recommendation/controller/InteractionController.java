package com.susume.recommendation.controller;

import com.susume.recommendation.dto.InteractionHistoryRequest;
import com.susume.recommendation.dto.InteractionHistoryResponse;
import com.susume.recommendation.dto.RecordInteractionRequest;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.service.InteractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
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
     * Retrieve user interaction history with pagination and filters.
     * GET /api/v1/interactions/history/
     * Authenticated via API key (TenantContext)
     */
    @GetMapping("history/")
    public ResponseEntity<?> getUserInteractionHistory(
            @Valid @RequestBody InteractionHistoryRequest interactionHistoryRequest) {

        Page<Interaction> page = interactionService.getUserInteractions(interactionHistoryRequest);
        var data = page.getContent()
                .stream()
                .map(interactionService::toHistoryItemResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new InteractionHistoryResponse(data, interactionHistoryRequest.limit()));
    }

}
