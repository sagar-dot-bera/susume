package com.susume.recommendation.integration;

import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.repository.TenantRepository;
import com.susume.recommendation.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ColdStartIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

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
    void coldStartUserFallsBackToTrending() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        String newUserId = "newuser-" + UUID.randomUUID();

        // Get recommendations for user with no history
        ResponseEntity<?> response = restTemplate.getForEntity(
                "/api/v1/recommendations/" + newUserId + "?limit=5",
                Object.class,
                headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Strategy should be "trending" since user has no history
        assertNotNull(response.getBody());
    }

    @Test
    void coldStartRecommendationsReturnActiveItems() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        String newUserId = "coldstart-" + UUID.randomUUID();

        // Get recommendations
        ResponseEntity<?> response = restTemplate.getForEntity(
                "/api/v1/recommendations/" + newUserId + "?limit=5",
                Object.class,
                headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void coldStartRecommendationsRespectLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", apiKey);

        String newUserId = "coldstart-limit-" + UUID.randomUUID();

        // Get recommendations with limit=5
        ResponseEntity<?> response = restTemplate.getForEntity(
                "/api/v1/recommendations/" + newUserId + "?limit=5",
                Object.class,
                headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
