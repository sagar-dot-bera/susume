package com.susume.recommendation.service;

import com.susume.recommendation.dto.InteractionHistoryItemResponse;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InteractionService {

    private final InteractionRepository interactionRepository;
    private final ItemRepository itemRepository;

    public InteractionService(InteractionRepository interactionRepository, ItemRepository itemRepository) {
        this.interactionRepository = interactionRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * Record a new interaction.
     */
    @CacheEvict(value = "recommendation", key = "#tenantId + ':' #externalUserId")
    @Transactional
    public Interaction recordInteraction(UUID tenantId, String externalUserId,
            String externalItemId, String interactionTypeStr,
            Instant timestamp) {
        // Validate interaction type
        InteractionType interactionType = InteractionType.fromString(interactionTypeStr);
        if (interactionType == null) {
            throw new IllegalArgumentException("Invalid interaction type: " + interactionTypeStr);
        }

        // Validate that the item exists and is ACTIVE
        var item = itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        if (!"ACTIVE".equals(item.getStatus())) {
            throw new IllegalArgumentException("Item is not active");
        }

        // Set timestamp to now if not provided
        if (timestamp == null) {
            timestamp = Instant.now();
        }

        // Create and save interaction
        Interaction interaction = Interaction.builder()
                .tenantId(tenantId)
                .externalUserId(externalUserId)
                .externalItemId(externalItemId)
                .interactionType(interactionType)
                .timestamp(timestamp)
                .build();

        Interaction savedInteraction = interactionRepository.save(interaction);
        log.info("Interaction recorded: {} for tenant: {}", savedInteraction.getId(), tenantId);

        return savedInteraction;
    }

    /**
     * Get user interaction history with optional filters.
     */
    public Page<Interaction> getUserInteractions(UUID tenantId, String externalUserId,
            int limit, Instant since,
            String interactionType, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, limit, Sort.by("timestamp").descending());

        if (since != null && interactionType != null) {
            InteractionType type = InteractionType.fromString(interactionType);
            if (type == null) {
                throw new IllegalArgumentException("Invalid interaction type");
            }

            Page<Interaction> result = interactionRepository.findByTenantIdAndExternalUserIdAndInteractionType(
                    tenantId, externalUserId, type, pageable);

            // Filter by timestamp if needed
            return new org.springframework.data.domain.PageImpl<>(
                    result.getContent().stream()
                            .filter(i -> i.getTimestamp().isAfter(since) || i.getTimestamp().equals(since))
                            .collect(Collectors.toList()),
                    pageable,
                    result.getTotalElements());
        } else if (since != null) {
            return interactionRepository.findByTenantIdAndExternalUserIdAndTimestampAfter(
                    tenantId, externalUserId, since, pageable);
        } else if (interactionType != null) {
            InteractionType type = InteractionType.fromString(interactionType);
            if (type == null) {
                throw new IllegalArgumentException("Invalid interaction type");
            }
            return interactionRepository.findByTenantIdAndExternalUserIdAndInteractionType(
                    tenantId, externalUserId, type, pageable);
        } else {
            return interactionRepository.findByTenantIdAndExternalUserId(tenantId, externalUserId, pageable);
        }
    }

    /**
     * Convert interaction to response DTO.
     */
    public InteractionHistoryItemResponse toHistoryItemResponse(Interaction interaction) {
        return new InteractionHistoryItemResponse(
                interaction.getId(),
                interaction.getExternalItemId(),
                interaction.getInteractionType().name(),
                interaction.getTimestamp());
    }
}
