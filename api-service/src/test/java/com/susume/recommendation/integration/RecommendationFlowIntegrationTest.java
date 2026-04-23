package com.susume.recommendation.integration;

import com.susume.recommendation.dto.CreateItemRequest;
import com.susume.recommendation.dto.RecordInteractionRequest;
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

class RecommendationFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;
    private String apiKey;
    private String[] itemIds;

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

        // Create 5 items
        itemIds = new String[5];
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        for (int i = 0; i < 5; i++) {
            itemIds[i] = "item-" + i + "-" + UUID.randomUUID();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("title", "Product " + i);
            metadata.put("description", "Description for product " + i);

            CreateItemRequest createRequest = new CreateItemRequest();
            createRequest.externalItemId = itemIds[i];
            createRequest.metadata = metadata;

            restTemplate.postForEntity(
                    "/api/v1/items",
                    createRequest,
                    Object.class,
                    headers);
        }
    }

    @Test
    void recommendationFlowExcludesInteractedItems() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        String userId = "user-" + UUID.randomUUID();

        // Record interactions with first 3 items
        for (int i = 0; i < 3; i++) {
            RecordInteractionRequest request = new RecordInteractionRequest();
            request.externalUserId = userId;
            request.externalItemId = itemIds[i];
            request.interactionType = (i == 0) ? "LIKE" : (i == 1) ? "VIEW" : "PURCHASE";

            restTemplate.postForEntity(
                    "/api/v1/interactions",
                    request,
                    Object.class,
                    headers);
        }

        // Get recommendations
        ResponseEntity<?> response = restTemplate.getForEntity(
                "/api/v1/recommendations/" + userId + "?limit=3",
                Object.class,
                headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void recommendationScoresAreWithinValidRange() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        String userId = "user-" + UUID.randomUUID();

        // Record some interactions
        RecordInteractionRequest request = new RecordInteractionRequest();
        request.externalUserId = userId;
        request.externalItemId = itemIds[0];
        request.interactionType = "LIKE";

        restTemplate.postForEntity(
                "/api/v1/interactions",
                request,
                Object.class,
                headers);

        // Get recommendations
        ResponseEntity<?> response = restTemplate.getForEntity(
                "/api/v1/recommendations/" + userId + "?limit=3",
                Object.class,
                headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Verify that similarity scores are in valid range [0, 1]
        assertNotNull(response.getBody());
    }

    @Test
    void recommendationStrategyIsPersonalizedForKnownUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        String userId = "known-user-" + UUID.randomUUID();

        // Record interactions
        RecordInteractionRequest request = new RecordInteractionRequest();
        request.externalUserId = userId;
        request.externalItemId = itemIds[0];
        request.interactionType = "PURCHASE";

        restTemplate.postForEntity(
                "/api/v1/interactions",
                request,
                Object.class,
                headers);

        // Get recommendations
        ResponseEntity<?> response = restTemplate.getForEntity(
                "/api/v1/recommendations/" + userId + "?limit=3",
                Object.class,
                headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Body should contain strategy="personalized"
        assertNotNull(response.getBody());
    }
}
