package com.susume.recommendation.service;

import com.susume.recommendation.dto.TenantInfoResponse;
import com.susume.recommendation.dto.TenantRegistrationRequest;
import com.susume.recommendation.entity.ApiKey;
import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.entity.User;
import com.susume.recommendation.exception.TenantNotFoundException;
import com.susume.recommendation.repository.ApiKeyRepository;
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
    private final ApiKeyRepository apiKeyRepository;

    public TenantService(TenantRepository tenantRepository, ApiKeyRepository apiKeyRepository) {
        this.tenantRepository = tenantRepository;
        this.apiKeyRepository = apiKeyRepository;
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

                .status("ACTIVE")
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);

        // Save ApiKey record
        ApiKey apiKey = ApiKey.builder()
                .tenantId(savedTenant.getId())
                .key(apiKeyHash)
                .build();
        apiKeyRepository.save(apiKey);

        // Log without exposing the raw key
        log.info("Tenant registered: {}", savedTenant.getId());

        // Return both the tenant and the raw key (to be returned only once in the
        // response)
        return new TenantRegistrationData(savedTenant, rawApiKey);
    }

    /**
     * Find tenant by API key hash (for authentication).
     */
    public Optional<Tenant> findByApiKeyHash(String apiKeyHash) {
        return apiKeyRepository.findByKey(apiKeyHash)
                .flatMap(apiKey -> tenantRepository.findById(apiKey.getTenantId()));
    }

    public Optional<Tenant> findById(UUID tenantId) {
        return tenantRepository.findById(tenantId);
    }

    public Tenant createTenant(TenantRegistrationRequest tenantRegistrationRequest, String email) {

        if (tenantRegistrationRequest.getName() == null || tenantRegistrationRequest.getName().isBlank()) {
            throw new IllegalArgumentException("Tenant name cannot be null or blank");
        }
        // Check if contact email already exists
        if (tenantRepository.findByContactEmail(email).isPresent()) {
            throw new IllegalArgumentException("Tenant with this email already exists");
        }

        if (tenantRepository.findBySlug(tenantRegistrationRequest.getSlug()).isPresent()) {
            throw new IllegalArgumentException("Tenant with this slug already exists");
        }

        // Create tenant
        Tenant tenant = Tenant.builder()
                .name(tenantRegistrationRequest.getName())
                .contactEmail(email)

                .status("ACTIVE")
                .slug(tenantRegistrationRequest.getSlug())
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);

        // Log without exposing the raw key
        log.info("Tenant registered: {}", savedTenant.getId());

        return savedTenant;
    }

    boolean tenantExistsByEmail(String email) {
        return tenantRepository.findByContactEmail(email).isPresent();
    }

    boolean tenantExistsBySlug(String slug) {
        return tenantRepository.findBySlug(slug).isPresent();
    }

    boolean tenantExistsById(UUID id) {
        return tenantRepository.findById(id).isPresent();
    }

    public Tenant featchTenant(UUID id) {
        return fetchTenant(id);
    }

    public Tenant fetchTenant(UUID id) {
        if (!tenantExistsById(id)) {
            log.error("Tenant with id:" + id);
            throw new TenantNotFoundException(id.toString());
        }

        return tenantRepository.findById(id).get();
    }

    public TenantInfoResponse tenantToTenantInfoResponse(Tenant tenant) {
        TenantInfoResponse tenantInfoResponse = new TenantInfoResponse();

        tenantInfoResponse.setId(tenant.getId());
        tenantInfoResponse.setContactEmail(tenant.getContactEmail());
        tenantInfoResponse.setCreatedAt(tenant.getCreatedAt());
        tenantInfoResponse.setName(tenant.getName());
        tenantInfoResponse.setSlug(tenant.getSlug());

        return tenantInfoResponse;
    }

}
