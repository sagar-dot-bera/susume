package com.susume.recommendation.service;

import com.susume.recommendation.dto.InteractionHistoryItemResponse;
import com.susume.recommendation.dto.InteractionHistoryRequest;
import com.susume.recommendation.dto.RecordInteractionRequest;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    @CacheEvict(value = "recommendation", key = "#tenantId + ':' + #recordInteractionRequest.externalUserId")
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

        return savedInteraction;
    }

    public Page<Interaction> getUserInteractions(InteractionHistoryRequest interactionHistoryRequest) {
        Pageable pageable = PageRequest.of(interactionHistoryRequest.pageNumber(), interactionHistoryRequest.limit(),
                Sort.by("timestamp").descending());

        InteractionType type = InteractionType.fromString(interactionHistoryRequest.interactionType());
        if (type == null) {
            throw new IllegalArgumentException("Invalid interaction type");
        }

        Page<Interaction> result = interactionRepository.findByTenantIdAndExternalUserIdAndInteractionType(
                interactionHistoryRequest.tenantId(), interactionHistoryRequest.externalUserId(), type, pageable);

        return new PageImpl<>(
                result.getContent().stream()
                        .filter(i -> i.getTimestamp().isAfter(interactionHistoryRequest.since())
                                || i.getTimestamp().equals(interactionHistoryRequest.since()))
                        .collect(Collectors.toList()),
                pageable,
                result.getTotalElements());
    }

    public InteractionHistoryItemResponse toHistoryItemResponse(Interaction interaction) {
        return new InteractionHistoryItemResponse(
                interaction.getId(),
                interaction.getExternalItemId(),
                interaction.getInteractionType().name(),
                interaction.getTimestamp());
    }
}
