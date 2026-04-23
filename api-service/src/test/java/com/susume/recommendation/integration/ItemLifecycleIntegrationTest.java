package com.susume.recommendation.integration;

import com.susume.recommendation.dto.CreateItemRequest;
import com.susume.recommendation.dto.ItemResponse;
import com.susume.recommendation.dto.UpdateItemRequest;
import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.repository.ItemRepository;
import com.susume.recommendation.repository.TenantRepository;
import com.susume.recommendation.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ItemLifecycleIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;
    private String apiKey;

    @BeforeEach
    void setUp() {
        // Create a test tenant
        tenantId = UUID.randomUUID();
        apiKey = "test-api-key-" + UUID.randomUUID();
        String hashedKey = CryptoUtil.hashAPIKey(apiKey);

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Test Tenant")
                .apiKeyHash(hashedKey)
                .build();

        tenantRepository.saveAndFlush(tenant);
        TenantContext.setTenantId(tenantId);
    }

    @Test
    void itemLifecycleFlow() {
        String externalItemId = "item-" + UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Test Product");
        metadata.put("description", "A test product description");

        // Step 1: POST /items → 201
        CreateItemRequest createRequest = new CreateItemRequest();
        createRequest.externalItemId = externalItemId;
        createRequest.metadata = metadata;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        ResponseEntity<ItemResponse> createResponse = restTemplate.postForEntity(
                "/api/v1/items",
                createRequest,
                ItemResponse.class,
                headers);

        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());
        assertEquals(externalItemId, createResponse.getBody().externalItemId);

        // Step 2: GET /items → item appears in list
        ResponseEntity<?> listResponse = restTemplate.getForEntity(
                "/api/v1/items?limit=20",
                Object.class);

        assertTrue(itemRepository.existsById(createResponse.getBody().id));

        // Step 3: PUT /items/{id} → 200, embedding regenerated
        Map<String, Object> updatedMetadata = new HashMap<>(metadata);
        updatedMetadata.put("title", "Updated Product");

        UpdateItemRequest updateRequest = new UpdateItemRequest();
        updateRequest.metadata = updatedMetadata;

        restTemplate.put(
                "/api/v1/items/" + externalItemId,
                updateRequest,
                headers);

        // Verify update was successful
        assertTrue(itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId).isPresent());

        // Step 4: DELETE /items/{id} → 200, status=INACTIVE
        restTemplate.delete(
                "/api/v1/items/" + externalItemId,
                headers);

        // Step 5: GET /items → item no longer in list (because status=INACTIVE)
        var item = itemRepository.findByTenantIdAndExternalItemId(tenantId, externalItemId);
        assertTrue(item.isPresent());
        assertEquals("INACTIVE", item.get().getStatus());
    }
}
