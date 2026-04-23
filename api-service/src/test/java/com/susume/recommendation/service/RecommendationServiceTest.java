package com.susume.recommendation.service;

import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private InteractionRepository interactionRepository;

    @Mock
    private ItemRepository itemRepository;

    private RecommendationService recommendationService;
    private UUID tenantId;
    private String userId;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                interactionRepository,
                itemRepository,
                50);
        tenantId = UUID.randomUUID();
        userId = "user123";
    }

    @Test
    void returnsPersonalizedResultsWhenInteractionsExist() {
        // Setup: user has interactions
        float[] embedding1 = { 1.0f, 0.0f, 0.0f };
        float[] embedding2 = { 0.5f, 0.5f, 0.0f };
        float[] embedding3 = { 0.0f, 1.0f, 0.0f };

        Interaction interaction1 = Interaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalUserId(userId)
                .externalItemId("item1")
                .interactionType(InteractionType.PURCHASE)
                .timestamp(Instant.now())
                .build();

        Interaction interaction2 = Interaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalUserId(userId)
                .externalItemId("item2")
                .interactionType(InteractionType.LIKE)
                .timestamp(Instant.now())
                .build();

        Item item1 = Item.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalItemId("item1")
                .embedding(embedding1)
                .status("ACTIVE")
                .build();

        Item item2 = Item.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalItemId("item2")
                .embedding(embedding2)
                .status("ACTIVE")
                .build();

        Item candidateItem = Item.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalItemId("item3")
                .embedding(embedding3)
                .status("ACTIVE")
                .build();

        when(interactionRepository.findByTenantIdAndExternalUserId(tenantId, userId))
                .thenReturn(Arrays.asList(interaction1, interaction2));
        when(itemRepository.findActiveByTenantIdAndExternalItemId(tenantId, "item1"))
                .thenReturn(item1);
        when(itemRepository.findActiveByTenantIdAndExternalItemId(tenantId, "item2"))
                .thenReturn(item2);
        when(itemRepository.findByTenantIdAndStatus(eq(tenantId), eq("ACTIVE"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Arrays.asList(item1, item2, candidateItem)));

        // Execute
        RecommendationResponse response = recommendationService.getRecommendations(tenantId, userId, 10);

        // Assert
        assertNotNull(response);
        assertEquals("personalized", response.getStrategy());
        assertTrue(response.getRecommendations().size() > 0);
        // Verify item3 is recommended but not item1 or item2
        assertTrue(response.getRecommendations().stream()
                .anyMatch(r -> r.getExternalItemId().equals("item3")));
        assertFalse(response.getRecommendations().stream()
                .anyMatch(r -> r.getExternalItemId().equals("item1")));
        assertFalse(response.getRecommendations().stream()
                .anyMatch(r -> r.getExternalItemId().equals("item2")));
    }

    @Test
    void fallsBackToTrendingWhenUserHasNoInteractions() {
        when(interactionRepository.findByTenantIdAndExternalUserId(tenantId, userId))
                .thenReturn(new ArrayList<>());
        when(interactionRepository.findTrendingItemIds(eq(tenantId), any(Instant.class), any()))
                .thenReturn(new ArrayList<>());

        RecommendationResponse response = recommendationService.getRecommendations(tenantId, userId, 10);

        assertEquals("trending", response.getStrategy());
        verify(interactionRepository).findTrendingItemIds(eq(tenantId), any(Instant.class), any());
    }

    @Test
    void excludesAlreadyInteractedItems() {
        float[] embedding = { 1.0f, 0.0f, 0.0f };

        Interaction interaction = Interaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalUserId(userId)
                .externalItemId("item1")
                .interactionType(InteractionType.LIKE)
                .timestamp(Instant.now())
                .build();

        Item item1 = Item.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalItemId("item1")
                .embedding(embedding)
                .status("ACTIVE")
                .build();

        Item item2 = Item.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalItemId("item2")
                .embedding(embedding)
                .status("ACTIVE")
                .build();

        when(interactionRepository.findByTenantIdAndExternalUserId(tenantId, userId))
                .thenReturn(Arrays.asList(interaction));
        when(itemRepository.findActiveByTenantIdAndExternalItemId(tenantId, "item1"))
                .thenReturn(item1);
        when(itemRepository.findByTenantIdAndStatus(eq(tenantId), eq("ACTIVE"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Arrays.asList(item1, item2)));

        RecommendationResponse response = recommendationService.getRecommendations(tenantId, userId, 10);

        assertFalse(response.getRecommendations().stream()
                .anyMatch(r -> r.getExternalItemId().equals("item1")));
    }

    @Test
    void excludesInactiveItems() {
        float[] embedding = { 1.0f, 0.0f, 0.0f };

        Interaction interaction = Interaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalUserId(userId)
                .externalItemId("item1")
                .interactionType(InteractionType.LIKE)
                .timestamp(Instant.now())
                .build();

        Item item1 = Item.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalItemId("item1")
                .embedding(embedding)
                .status("ACTIVE")
                .build();

        Item inactiveItem = Item.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalItemId("item2")
                .embedding(embedding)
                .status("INACTIVE")
                .build();

        when(interactionRepository.findByTenantIdAndExternalUserId(tenantId, userId))
                .thenReturn(Arrays.asList(interaction));
        when(itemRepository.findActiveByTenantIdAndExternalItemId(tenantId, "item1"))
                .thenReturn(item1);
        when(itemRepository.findByTenantIdAndStatus(eq(tenantId), eq("ACTIVE"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Arrays.asList(item1))); // Only active items are in this query

        RecommendationResponse response = recommendationService.getRecommendations(tenantId, userId, 10);

        assertFalse(response.getRecommendations().stream()
                .anyMatch(r -> r.getExternalItemId().equals("item2")));
    }

    @Test
    void respectsLimitParameter() {
        float[] embedding = { 1.0f, 0.0f, 0.0f };

        Interaction interaction = Interaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalUserId(userId)
                .externalItemId("item1")
                .interactionType(InteractionType.LIKE)
                .timestamp(Instant.now())
                .build();

        Item item1 = Item.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalItemId("item1")
                .embedding(embedding)
                .status("ACTIVE")
                .build();

        List<Item> candidates = new ArrayList<>();
        for (int i = 2; i <= 10; i++) {
            candidates.add(Item.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .externalItemId("item" + i)
                    .embedding(embedding)
                    .status("ACTIVE")
                    .build());
        }

        when(interactionRepository.findByTenantIdAndExternalUserId(tenantId, userId))
                .thenReturn(Arrays.asList(interaction));
        when(itemRepository.findActiveByTenantIdAndExternalItemId(tenantId, "item1"))
                .thenReturn(item1);
        when(itemRepository.findByTenantIdAndStatus(eq(tenantId), eq("ACTIVE"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(candidates));

        RecommendationResponse response = recommendationService.getRecommendations(tenantId, userId, 3);

        assertTrue(response.getRecommendations().size() <= 3);
    }
}
