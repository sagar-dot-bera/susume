package com.susume.recommendation.controller;

import com.susume.recommendation.dto.ErrorResponse;
import com.susume.recommendation.dto.InteractionHistoryResponse;
import com.susume.recommendation.dto.InteractionResponse;
import com.susume.recommendation.dto.RecordInteractionRequest;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.service.InteractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.Base64;
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
     */
    @PostMapping
    public ResponseEntity<?> recordInteraction(@Valid @RequestBody RecordInteractionRequest request) {
        try {
            UUID tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Invalid API key"));
            }

            // Validate required fields
            if (request.externalUserId == null || request.externalUserId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("BAD_REQUEST", "externalUserId is required"));
            }

            if (request.externalItemId == null || request.externalItemId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("BAD_REQUEST", "externalItemId is required"));
            }

            if (request.interactionType == null || request.interactionType.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("BAD_REQUEST", "interactionType is required"));
            }

            Interaction interaction = interactionService.recordInteraction(
                    tenantId,
                    request.externalUserId,
                    request.externalItemId,
                    request.interactionType,
                    request.timestamp);

            InteractionResponse response = new InteractionResponse(
                    interaction.getId(),
                    interaction.getExternalUserId(),
                    interaction.getExternalItemId(),
                    interaction.getInteractionType().name(),
                    interaction.getTimestamp());

            log.info("Interaction recorded: {}", interaction.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Item not found: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "Item not found"));
            }
            if (e.getMessage().contains("not active")) {
                log.warn("Item not active: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "Item is not active"));
            }
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));

        } catch (Exception e) {
            log.error("Error recording interaction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error recording interaction"));
        }
    }

    /**
     * Retrieve user interaction history with pagination and filters.
     * GET
     * /api/v1/interactions/{externalUserId}?limit=50&cursor=<opaque>&interactionType=PURCHASE&since=ISO8601
     */
    @GetMapping("/{externalUserId}")
    public ResponseEntity<?> getUserInteractionHistory(
            @PathVariable String externalUserId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String interactionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        try {
            UUID tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Invalid API key"));
            }

            // Decode cursor to get page number
            int pageNumber = 0;
            if (cursor != null && !cursor.isEmpty()) {
                try {
                    String decoded = new String(Base64.getDecoder().decode(cursor));
                    pageNumber = Integer.parseInt(decoded);
                } catch (Exception e) {
                    log.warn("Invalid cursor format");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new ErrorResponse("BAD_REQUEST", "Invalid cursor format"));
                }
            }

            // Limit max size to avoid huge queries
            if (limit > 200) {
                limit = 200;
            }

            Page<Interaction> page = interactionService.getUserInteractions(
                    tenantId, externalUserId, limit, since, interactionType, pageNumber);

            var data = page.getContent()
                    .stream()
                    .map(interactionService::toHistoryItemResponse)
                    .collect(Collectors.toList());

            // Generate next cursor if more results exist
            String nextCursor = null;
            if (page.hasNext()) {
                nextCursor = Base64.getEncoder().encodeToString(String.valueOf(pageNumber + 1).getBytes());
            }

            InteractionHistoryResponse response = new InteractionHistoryResponse(data, nextCursor, limit);

            log.info("Interaction history retrieved for user: {}, tenant: {}, count: {}",
                    externalUserId, tenantId, data.size());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));

        } catch (Exception e) {
            log.error("Error retrieving interaction history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error retrieving interaction history"));
        }
    }
}
