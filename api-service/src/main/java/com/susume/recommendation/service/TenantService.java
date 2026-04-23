package com.susume.recommendation.service;

import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.repository.TenantRepository;
import com.susume.recommendation.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * Register a new tenant and generate an API key.
     * Returns the raw API key (only once), which is hashed for storage.
     */
    @Transactional
    public TenantRegistrationData register(String name, String contactEmail) {
        // Check if contact email already exists
        if (tenantRepository.findByContactEmail(contactEmail).isPresent()) {
            throw new IllegalArgumentException("Tenant with this email already exists");
        }

        // Generate API key and hash it
        String rawApiKey = CryptoUtil.generateAPIKey();
        String apiKeyHash = CryptoUtil.hashAPIKey(rawApiKey);

        // Create tenant
        Tenant tenant = Tenant.builder()
                .name(name)
                .contactEmail(contactEmail)
                .apiKeyHash(apiKeyHash)
                .status("ACTIVE")
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);

        // Log without exposing the raw key
        log.info("Tenant registered: {}", savedTenant.getId());

        // Return both the tenant and the raw key (to be returned only once in the
        // response)
        return new TenantRegistrationData(savedTenant, rawApiKey);
    }

    /**
     * Regenerate API key for a tenant (atomically).
     */
    @Transactional
    public String regenerateApiKey(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        String newRawApiKey = CryptoUtil.generateAPIKey();
        String newApiKeyHash = CryptoUtil.hashAPIKey(newRawApiKey);

        tenant.setApiKeyHash(newApiKeyHash);
        tenantRepository.save(tenant);

        log.info("API key regenerated for tenant: {}", tenantId);

        return newRawApiKey;
    }

    /**
     * Find tenant by API key hash (for authentication).
     */
    public Optional<Tenant> findByApiKeyHash(String apiKeyHash) {
        return tenantRepository.findByApiKeyHash(apiKeyHash);
    }

    public Optional<Tenant> findById(UUID tenantId) {
        return tenantRepository.findById(tenantId);
    }
}
