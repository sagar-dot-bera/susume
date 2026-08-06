package com.susume.recommendation.service;

import com.susume.recommendation.client.EmbeddingServiceClient;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private EmbeddingServiceClient embeddingServiceClient;

    @Mock
    private ItemEventPublisher itemEventPublisher;

    private ItemService itemService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        itemService = new ItemService(itemRepository, embeddingServiceClient, itemEventPublisher);
        tenantId = UUID.randomUUID();
    }

    @Test
    void createItemCallsEmbeddingService() {
        String externalItemId = "external-id-123";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Product Title");
        metadata.put("description", "Product Description");

        float[] embedding = { 0.1f, 0.2f, 0.3f };
        when(itemRepository.save(any())).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        Item result = itemService.createItem(tenantId, externalItemId, metadata);

        assertNotNull(result);
        assertEquals(externalItemId, result.getExternalItemId());
        assertEquals(tenantId, result.getTenantId());
        verify(itemEventPublisher).publish(any());
        verify(itemRepository).save(any());
    }

    @Test
    void createItemThrows409OnDuplicateExternalId() {
        String externalItemId = "external-id-123";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Product Title");

        Item existingItem = Item.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .externalItemId(externalItemId)
                .metadata(metadata)
                .status("ACTIVE")
                .build();

        when(itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId))
                .thenReturn(Optional.of(existingItem));

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(tenantId, externalItemId, metadata));
    }

    @Test
    void updateItemRegeneratesEmbedding() {
        String externalItemId = "external-id-123";
        UUID itemId = UUID.randomUUID();

        Map<String, Object> originalMetadata = new HashMap<>();
        originalMetadata.put("title", "Original Title");

        Map<String, Object> updatedMetadata = new HashMap<>();
        updatedMetadata.put("title", "Updated Title");

        Item existingItem = Item.builder()
                .id(itemId)
                .tenantId(tenantId)
                .externalItemId(externalItemId)
                .metadata(originalMetadata)
                .embedding(new float[] { 0.1f, 0.2f })
                .status("ACTIVE")
                .build();

        float[] newEmbedding = { 0.3f, 0.4f };

        when(itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId))
                .thenReturn(Optional.of(existingItem));
        when(embeddingServiceClient.getEmbedding(any())).thenReturn(newEmbedding);
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Item result = itemService.updateItem(tenantId, externalItemId, updatedMetadata);

        assertNotNull(result);
        assertArrayEquals(newEmbedding, result.getEmbedding());
        assertTrue(result.getMetadata().containsValue("Updated Title"));
        verify(embeddingServiceClient).getEmbedding(any());
        verify(itemRepository).save(any());
    }

    @Test
    void deleteItemSetsSoftDeleteStatus() {
        String externalItemId = "external-id-123";
        UUID itemId = UUID.randomUUID();

        Item item = Item.builder()
                .id(itemId)
                .tenantId(tenantId)
                .externalItemId(externalItemId)
                .metadata(new HashMap<>())
                .status("ACTIVE")
                .build();

        when(itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId))
                .thenReturn(Optional.of(item));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        itemService.deleteItem(tenantId, externalItemId);

        assertEquals("INACTIVE", item.getStatus());
        verify(itemRepository).save(item);
    }

    @Test
    void createItemThrowsOnInvalidMetadata() {
        String externalItemId = "external-id-123";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("empty", "");
        metadata.put("null_field", null);

        assertThrows(IllegalArgumentException.class, () -> itemService.createItem(tenantId, externalItemId, metadata));
    }

    @Test
    void updateItemThrowsOnNonExistentItem() {
        String externalItemId = "non-existent-id";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Updated Title");

        when(itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> itemService.updateItem(tenantId, externalItemId, metadata));
    }
}
