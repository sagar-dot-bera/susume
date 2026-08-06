package com.susume.recommendation.strategy;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationResult;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.repository.InteractionRepository;
import com.susume.recommendation.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrategyTests {

    private InteractionRepository interactionRepository;
    private ItemRepository itemRepository;
    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        interactionRepository = mock(InteractionRepository.class);
        itemRepository = mock(ItemRepository.class);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void testPopularityStrategy() {
        PopularityStrategy strategy = new PopularityStrategy(interactionRepository, itemRepository);
        Interaction interaction = new Interaction();
        interaction.setExternalItemId("item1");
        interaction.setInteractionType(InteractionType.LIKE);

        when(interactionRepository.findByTenantId(tenantId)).thenReturn(List.of(interaction));
        Item item = Item.builder().externalItemId("item1").metadata(Map.of("name", "Test Item")).build();
        when(itemRepository.findActiveByTenantIdAndExternalItemId(tenantId, "item1")).thenReturn(item);

        RecommendationContext context = RecommendationContext.builder()
                .tenantId(tenantId)
                .limit(5)
                .build();

        RecommendationResult result = strategy.recommend(context);
        assertEquals("popularity", result.getStrategy());
        assertFalse(result.getItems().isEmpty());
        assertEquals("item1", result.getItems().get(0).getExternalItemId());
    }

    @Test
    void testRandomDiscoveryStrategy() {
        RandomDiscoveryStrategy strategy = new RandomDiscoveryStrategy(itemRepository);
        Item item1 = Item.builder().externalItemId("item1").build();
        Item item2 = Item.builder().externalItemId("item2").build();
        when(itemRepository.findByTenantIdAndStatus(eq(tenantId), eq("ACTIVE"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(item1, item2)));

        RecommendationContext context = RecommendationContext.builder()
                .tenantId(tenantId)
                .limit(2)
                .build();

        RecommendationResult result = strategy.recommend(context);
        assertEquals("random_discovery", result.getStrategy());
        assertEquals(2, result.getItems().size());
    }
}
