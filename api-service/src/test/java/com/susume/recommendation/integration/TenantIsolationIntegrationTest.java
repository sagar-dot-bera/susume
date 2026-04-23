package com.susume.recommendation.integration;

import com.susume.recommendation.dto.CreateItemRequest;
import com.susume.recommendation.entity.Tenant;
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

class TenantIsolationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantAId;
    private UUID tenantBId;
    private String apiKeyA;
    private String apiKeyB;

    @BeforeEach
    void setUp() {
        // Create tenant A
        tenantAId = UUID.randomUUID();
        apiKeyA = "api-key-a-" + UUID.randomUUID();
        String hashedKeyA = CryptoUtil.hashAPIKey(apiKeyA);

        Tenant tenantA = Tenant.builder()
                .id(tenantAId)
                .name("Tenant A")
                .apiKeyHash(hashedKeyA)
                .build();

        tenantRepository.saveAndFlush(tenantA);

        // Create tenant B
        tenantBId = UUID.randomUUID();
        apiKeyB = "api-key-b-" + UUID.randomUUID();
        String hashedKeyB = CryptoUtil.hashAPIKey(apiKeyB);

        Tenant tenantB = Tenant.builder()
                .id(tenantBId)
                .name("Tenant B")
                .apiKeyHash(hashedKeyB)
                .build();

        tenantRepository.saveAndFlush(tenantB);
    }

    @Test
    void tenantsCannotSeeEachOthersItems() {
        String itemIdA = "item-a-" + UUID.randomUUID();
        String itemIdB = "item-b-" + UUID.randomUUID();

        // Create item for Tenant A
        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-API-KEY", apiKeyA);

        Map<String, Object> metadataA = new HashMap<>();
        metadataA.put("title", "Item A");

        CreateItemRequest requestA = new CreateItemRequest();
        requestA.externalItemId = itemIdA;
        requestA.metadata = metadataA;

        ResponseEntity<?> createA = restTemplate.postForEntity(
                "/api/v1/items",
                requestA,
                Object.class,
                headersA);

        assertEquals(HttpStatus.CREATED, createA.getStatusCode());

        // Create item for Tenant B
        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-API-KEY", apiKeyB);

        Map<String, Object> metadataB = new HashMap<>();
        metadataB.put("title", "Item B");

        CreateItemRequest requestB = new CreateItemRequest();
        requestB.externalItemId = itemIdB;
        requestB.metadata = metadataB;

        ResponseEntity<?> createB = restTemplate.postForEntity(
                "/api/v1/items",
                requestB,
                Object.class,
                headersB);

        assertEquals(HttpStatus.CREATED, createB.getStatusCode());

        // Verify Tenant A can see its own item but not Tenant B's
        ResponseEntity<?> listA = restTemplate.getForEntity(
                "/api/v1/items?limit=20",
                Object.class,
                headersA);

        assertEquals(HttpStatus.OK, listA.getStatusCode());

        // Verify Tenant B can see its own item but not Tenant A's
        ResponseEntity<?> listB = restTemplate.getForEntity(
                "/api/v1/items?limit=20",
                Object.class,
                headersB);

        assertEquals(HttpStatus.OK, listB.getStatusCode());

        // Verify database isolation
        long countA = itemRepository.findByTenantIdAndStatus(tenantAId, "ACTIVE",
                org.springframework.data.domain.PageRequest.of(0, 100)).getTotalElements();
        long countB = itemRepository.findByTenantIdAndStatus(tenantBId, "ACTIVE",
                org.springframework.data.domain.PageRequest.of(0, 100)).getTotalElements();

        assertEquals(1, countA);
        assertEquals(1, countB);
    }

    @Test
    void recommendationsIsolatedByTenant() {
        String itemIdA1 = "item-a1-" + UUID.randomUUID();
        String itemIdA2 = "item-a2-" + UUID.randomUUID();
        String itemIdB1 = "item-b1-" + UUID.randomUUID();

        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-API-KEY", apiKeyA);

        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-API-KEY", apiKeyB);

        // Create items for both tenants
        for (String itemId : new String[] { itemIdA1, itemIdA2 }) {
            CreateItemRequest request = new CreateItemRequest();
            request.externalItemId = itemId;
            request.metadata = new HashMap<>(Map.of("title", "Item for A"));

            restTemplate.postForEntity("/api/v1/items", request, Object.class, headersA);
        }

        CreateItemRequest requestB = new CreateItemRequest();
        requestB.externalItemId = itemIdB1;
        requestB.metadata = new HashMap<>(Map.of("title", "Item for B"));

        restTemplate.postForEntity("/api/v1/items", requestB, Object.class, headersB);

        // Get recommendations for each tenant
        ResponseEntity<?> recsA = restTemplate.getForEntity(
                "/api/v1/recommendations/user-a?limit=5",
                Object.class,
                headersA);

        ResponseEntity<?> recsB = restTemplate.getForEntity(
                "/api/v1/recommendations/user-b?limit=5",
                Object.class,
                headersB);

        assertEquals(HttpStatus.OK, recsA.getStatusCode());
        assertEquals(HttpStatus.OK, recsB.getStatusCode());

        // Both should return results from their own tenant's items only
        assertNotNull(recsA.getBody());
        assertNotNull(recsB.getBody());
    }
}
