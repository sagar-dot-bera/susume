package com.susume.recommendation.controller;

import com.susume.recommendation.dto.CreateItemRequest;
import com.susume.recommendation.dto.ErrorResponse;
import com.susume.recommendation.dto.ItemDetailResponse;
import com.susume.recommendation.dto.ItemListResponse;
import com.susume.recommendation.dto.ItemResponse;
import com.susume.recommendation.dto.UpdateItemRequest;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.exception.EmbeddingServiceException;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.service.ItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/items")
@Slf4j
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * Create a new item.
     * POST /api/v1/items
     */
    @PostMapping
    public ResponseEntity<?> createItem(@Valid @RequestBody CreateItemRequest request) {
        try {
            UUID tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Invalid API key"));
            }

            Item item = itemService.createItem(tenantId, request.externalItemId, request.metadata);

            ItemResponse response = new ItemResponse(
                    item.getId(),
                    item.getExternalItemId(),
                    item.getStatus(),
                    item.getCreatedAt(),
                    item.getUpdatedAt());

            log.info("Item created: {}", item.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("already exists")) {
                log.warn("Duplicate item: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse("CONFLICT", e.getMessage()));
            }
            log.warn("Invalid metadata: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));

        } catch (EmbeddingServiceException e) {
            log.error("Embedding service error", e);
            if (e.isRetryable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErrorResponse("SERVICE_UNAVAILABLE", "Embedding service unavailable"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error processing embedding"));

        } catch (Exception e) {
            log.error("Error creating item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error creating item"));
        }
    }

    /**
     * Update an item's metadata.
     * PUT /api/v1/items/{externalItemId}
     */
    @PutMapping("/{externalItemId}")
    public ResponseEntity<?> updateItem(
            @PathVariable String externalItemId,
            @Valid @RequestBody UpdateItemRequest request) {
        try {
            UUID tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Invalid API key"));
            }

            Item item = itemService.updateItem(tenantId, externalItemId, request.metadata);

            ItemDetailResponse response = itemService.toDetailResponse(item);

            log.info("Item updated: {}", item.getId());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Item not found: {}", externalItemId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "Item not found"));
            }
            log.warn("Invalid metadata: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));

        } catch (EmbeddingServiceException e) {
            log.error("Embedding service error", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("SERVICE_UNAVAILABLE", "Embedding service unavailable"));

        } catch (Exception e) {
            log.error("Error updating item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error updating item"));
        }
    }

    /**
     * Soft-delete an item.
     * DELETE /api/v1/items/{externalItemId}
     */
    @DeleteMapping("/{externalItemId}")
    public ResponseEntity<?> deleteItem(@PathVariable String externalItemId) {
        try {
            UUID tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Invalid API key"));
            }

            itemService.deleteItem(tenantId, externalItemId);

            log.info("Item deleted: {}", externalItemId);
            return ResponseEntity.ok(new ItemResponse(null, externalItemId, "INACTIVE", null, null));

        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Item not found: {}", externalItemId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("NOT_FOUND", "Item not found"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));

        } catch (Exception e) {
            log.error("Error deleting item", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error deleting item"));
        }
    }

    /**
     * List items with pagination and optional filters.
     * GET
     * /api/v1/items?limit=20&cursor=<opaque>&createdAfter=ISO8601&createdBefore=ISO8601
     */
    @GetMapping
    public ResponseEntity<?> listItems(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore) {
        try {
            UUID tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("UNAUTHORIZED", "Invalid API key or JWT"));
            }

            // Decode cursor to get page number (cursor format: base64 encoded page number)
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
            if (limit > 100) {
                limit = 100;
            }

            Page<Item> page = itemService.listItems(tenantId, limit, createdAfter, createdBefore, pageNumber);

            List<ItemDetailResponse> items = page.getContent()
                    .stream()
                    .map(itemService::toDetailResponse)
                    .collect(Collectors.toList());

            // Generate next cursor if more results exist
            String nextCursor = null;
            if (page.hasNext()) {
                nextCursor = Base64.getEncoder().encodeToString(String.valueOf(pageNumber + 1).getBytes());
            }

            ItemListResponse response = new ItemListResponse(items, nextCursor, limit);

            log.info("Items listed for tenant: {}, count: {}", tenantId, items.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error listing items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error listing items"));
        }
    }
}
