package com.susume.recommendation.integration;

import com.susume.recommendation.dto.CreateItemRequest;
import com.susume.recommendation.dto.RecordInteractionRequest;
import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.repository.InteractionRepository;
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

class InteractionRecordingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InteractionRepository interactionRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;
    private String apiKey;
    private String itemId;

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

        // Create an item
        itemId = "item-" + UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Test Product");

        CreateItemRequest createRequest = new CreateItemRequest();
        createRequest.externalItemId = itemId;
        createRequest.metadata = metadata;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        restTemplate.postForEntity(
                "/api/v1/items",
                createRequest,
                Object.class,
                headers);
    }

    @Test
    void recordInteractionWithValidItem() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        RecordInteractionRequest request = new RecordInteractionRequest();
        request.externalUserId = "user123";
        request.externalItemId = itemId;
        request.interactionType = "LIKE";

        ResponseEntity<?> response = restTemplate.postForEntity(
                "/api/v1/interactions",
                request,
                Object.class,
                headers);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        long count = interactionRepository.count();
        assertTrue(count > 0);
    }

    @Test
    void recordInteractionWithInactiveItemReturns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        // First, delete the item to make it INACTIVE
        restTemplate.delete("/api/v1/items/" + itemId, headers);

        // Try to record interaction with inactive item
        RecordInteractionRequest request = new RecordInteractionRequest();
        request.externalUserId = "user123";
        request.externalItemId = itemId;
        request.interactionType = "LIKE";

        ResponseEntity<?> response = restTemplate.postForEntity(
                "/api/v1/interactions",
                request,
                Object.class,
                headers);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void recordInteractionWithUnknownTypeReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        RecordInteractionRequest request = new RecordInteractionRequest();
        request.externalUserId = "user123";
        request.externalItemId = itemId;
        request.interactionType = "INVALID_TYPE";

        ResponseEntity<?> response = restTemplate.postForEntity(
                "/api/v1/interactions",
                request,
                Object.class,
                headers);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void retrieveInteractionHistory() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        RecordInteractionRequest request = new RecordInteractionRequest();
        request.externalUserId = "user123";
        request.externalItemId = itemId;
        request.interactionType = "LIKE";

        // Record interaction
        restTemplate.postForEntity(
                "/api/v1/interactions",
                request,
                Object.class,
                headers);

        // Retrieve history
        ResponseEntity<?> response = restTemplate.getForEntity(
                "/api/v1/interactions/user123",
                Object.class,
                headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
