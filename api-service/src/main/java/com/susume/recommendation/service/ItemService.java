package com.susume.recommendation.service;

import com.susume.recommendation.client.EmbeddingServiceClient;
import com.susume.recommendation.dto.ItemDetailResponse;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.exception.EmbeddingServiceException;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.util.MetadataConcatenator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ItemService {

    private final ItemRepository itemRepository;
    private final EmbeddingServiceClient embeddingServiceClient;

    public ItemService(ItemRepository itemRepository, EmbeddingServiceClient embeddingServiceClient) {
        this.itemRepository = itemRepository;
        this.embeddingServiceClient = embeddingServiceClient;
    }

    /**
     * Create a new item with metadata.
     */
    @Transactional
    public Item createItem(UUID tenantId, String externalItemId, Map<String, Object> metadata) {
        // Validate metadata
        if (!MetadataConcatenator.isValid(metadata)) {
            throw new IllegalArgumentException("Metadata must contain at least one non-empty field");
        }

        // Check for duplicate externalItemId within tenant
        if (itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId).isPresent()) {
            throw new IllegalArgumentException("Item with this external ID already exists for this tenant");
        }

        // Concatenate metadata for embedding
        String concatenated = MetadataConcatenator.concatenate(metadata);

        // Call embedding service synchronously
        float[] embedding = embeddingServiceClient.getEmbedding(concatenated);

        // Create and save item
        Item item = Item.builder()
                .tenantId(tenantId)
                .externalItemId(externalItemId)
                .metadata(metadata)
                .embedding(embedding)
                .status("ACTIVE")
                .build();

        Item savedItem = itemRepository.save(item);
        log.info("Item created: {} for tenant: {}", savedItem.getId(), tenantId);

        return savedItem;
    }

    /**
     * Update an item's metadata and regenerate embedding.
     */
    @Transactional
    public Item updateItem(UUID tenantId, String externalItemId, Map<String, Object> metadata) {
        Item item = itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        // Merge metadata (partial update support)
        if (metadata != null && !metadata.isEmpty()) {
            Map<String, Object> updatedMetadata = new LinkedHashMap<>(item.getMetadata());
            updatedMetadata.putAll(metadata);
            item.setMetadata(updatedMetadata);
        }

        // Validate metadata
        if (!MetadataConcatenator.isValid(item.getMetadata())) {
            throw new IllegalArgumentException("Metadata must contain at least one non-empty field");
        }

        // Regenerate embedding
        String concatenated = MetadataConcatenator.concatenate(item.getMetadata());
        float[] newEmbedding = embeddingServiceClient.getEmbedding(concatenated);
        item.setEmbedding(newEmbedding);

        Item updatedItem = itemRepository.save(item);
        log.info("Item updated: {} for tenant: {}", updatedItem.getId(), tenantId);

        return updatedItem;
    }

    /**
     * Soft-delete an item (set status to INACTIVE).
     */
    @Transactional
    public void deleteItem(UUID tenantId, String externalItemId) {
        Item item = itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        item.setStatus("INACTIVE");
        itemRepository.save(item);
        log.info("Item deleted (soft): {} for tenant: {}", item.getId(), tenantId);
    }

    /**
     * List items with pagination and optional filters.
     */
    public Page<Item> listItems(UUID tenantId, int limit, Instant createdAfter, Instant createdBefore, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, limit);

        if (createdAfter != null && createdBefore != null) {
            return itemRepository.findByTenantIdAndStatusAndCreatedBetween(tenantId, "ACTIVE", createdAfter,
                    createdBefore, pageable);
        } else if (createdAfter != null) {
            return itemRepository.findByTenantIdAndStatusAndCreatedAfter(tenantId, "ACTIVE", createdAfter, pageable);
        } else if (createdBefore != null) {
            return itemRepository.findByTenantIdAndStatusAndCreatedBefore(tenantId, "ACTIVE", createdBefore, pageable);
        } else {
            return itemRepository.findByTenantIdAndStatus(tenantId, "ACTIVE", pageable);
        }
    }

    /**
     * Get a single item by ID (without embedding vector).
     */
    public Optional<Item> getItemById(UUID tenantId, String externalItemId) {
        return itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId);
    }

    /**
     * Convert item to response DTO (exclude embedding vector).
     */
    public ItemDetailResponse toDetailResponse(Item item) {
        return new ItemDetailResponse(
                item.getId(),
                item.getExternalItemId(),
                item.getMetadata(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
