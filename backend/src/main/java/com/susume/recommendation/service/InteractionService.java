package com.susume.recommendation.service;

import com.susume.recommendation.dto.InteractionExportItemResponse;
import com.susume.recommendation.dto.InteractionHistoryItemResponse;
import com.susume.recommendation.dto.RecordInteractionRequest;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class InteractionService {

    public static final int MAX_HISTORY_LIMIT = 100;

    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;
    private final RecommendationCacheEvictor recommendationCacheEvictor;

    public InteractionService(InteractionRepository interactionRepository, ItemRepository itemRepository,
            RecommendationCacheEvictor recommendationCacheEvictor) {
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
        this.recommendationCacheEvictor = recommendationCacheEvictor;
    }

    /**
     * Record a new interaction.
     */
    @Transactional
    public Interaction recordInteraction(UUID tenantId, RecordInteractionRequest recordInteractionRequest) {

        InteractionType interactionType = InteractionType.fromString(recordInteractionRequest.interactionType);

        // Validate that the item exists and is ACTIVE
        var item = itemRepository
                .findByTenantIdAndExternalItemId(tenantId, recordInteractionRequest.externalItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        if (!"ACTIVE".equals(item.getStatus())) {
            throw new IllegalArgumentException("Item is not active");
        }

        // Create and save interaction
        Interaction interaction = Interaction.builder()
                .tenantId(tenantId)
                .externalUserId(recordInteractionRequest.externalUserId)
                .externalItemId(recordInteractionRequest.externalItemId)
                .interactionType(interactionType)
                .timestamp(Instant.now())
                .build();

        Interaction savedInteraction = interactionRepository.save(interaction);
        log.info("Interaction recorded: {} for tenant: {}", savedInteraction.getId(), tenantId);

        // The user's behaviour changed, so all of their cached recommendation lists are stale.
        recommendationCacheEvictor.evictUser(tenantId, recordInteractionRequest.externalUserId);

        return savedInteraction;
    }

    /**
     * A user's interactions within the tenant, newest first. Type and start-time
     * filters are applied in the query so paging and totals stay correct.
     */
    public Page<Interaction> getUserInteractions(UUID tenantId, String externalUserId, String interactionType,
            Instant since, int pageNumber, int limit) {
        if (pageNumber < 0 || limit < 1 || limit > MAX_HISTORY_LIMIT) {
            throw new IllegalArgumentException("page must be >= 0 and limit between 1 and " + MAX_HISTORY_LIMIT);
        }

        Specification<Interaction> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("tenantId"), tenantId),
                cb.equal(root.get("externalUserId"), externalUserId));

        if (interactionType != null && !interactionType.isBlank()) {
            InteractionType type = InteractionType.fromString(interactionType);
            if (type == null) {
                throw new IllegalArgumentException("Invalid interaction type: " + interactionType);
            }
            spec = spec.and((root, query, cb) -> cb.equal(root.get("interactionType"), type));
        }
        if (since != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), since));
        }

        Pageable pageable = PageRequest.of(pageNumber, limit,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.asc("id")));
        return interactionRepository.findAll(spec, pageable);
    }

    public InteractionHistoryItemResponse toHistoryItemResponse(Interaction interaction) {
        return new InteractionHistoryItemResponse(
                interaction.getId(),
                interaction.getExternalItemId(),
                interaction.getInteractionType().name(),
                interaction.getTimestamp());
    }

    /**
     * Bulk export of all interactions for a tenant, paginated by timestamp ascending.
     * Used by the ML recommendation-service offline training pipeline to build
     * historical datasets without needing direct database access.
     */
    public Page<Interaction> exportInteractions(UUID tenantId, int pageNumber, int limit) {
        Pageable pageable = PageRequest.of(pageNumber, limit, Sort.by("timestamp").ascending());
        return interactionRepository.findByTenantId(tenantId, pageable);
    }

    public InteractionExportItemResponse toExportItemResponse(Interaction interaction) {
        return new InteractionExportItemResponse(
                interaction.getId(),
                interaction.getExternalUserId(),
                interaction.getExternalItemId(),
                interaction.getInteractionType().name(),
                interaction.getTimestamp());
    }
}
