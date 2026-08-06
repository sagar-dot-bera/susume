package com.susume.recommendation.service;

import com.susume.recommendation.dto.ApiKeyDto;
import com.susume.recommendation.dto.CreateApiKeyResponse;
import com.susume.recommendation.entity.ApiKey;
import com.susume.recommendation.repository.ApiKeyRepository;
import com.susume.recommendation.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Create a new API key for a tenant.
     * Generates a raw key, hashes it, stores the entity, and returns the raw key only once.
     */
    @Transactional
    public CreateApiKeyResponse createApiKey(UUID tenantId) {
        String rawApiKey = CryptoUtil.generateAPIKey();
        String hashedApiKey = CryptoUtil.hashAPIKey(rawApiKey);

        ApiKey apiKey = ApiKey.builder()
                .tenantId(tenantId)
                .key(hashedApiKey)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("Created new API key {} for tenant {}", saved.getId(), tenantId);

        return CreateApiKeyResponse.builder()
                .id(saved.getId())
                .tenantId(saved.getTenantId())
                .rawApiKey(rawApiKey)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    /**
     * List all API keys for a specific tenant.
     */
    @Transactional(readOnly = true)
    public List<ApiKeyDto> getApiKeysByTenant(UUID tenantId) {
        return apiKeyRepository.findByTenantId(tenantId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Get a specific API key by ID and tenant ID.
     */
    @Transactional(readOnly = true)
    public Optional<ApiKeyDto> getApiKeyById(UUID id, UUID tenantId) {
        return apiKeyRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toDto);
    }

    /**
     * Find an ApiKey entity by its key string (used for authentication).
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> findByKey(String key) {
        return apiKeyRepository.findByKey(key);
    }

    /**
     * Delete/Revoke an API key for a tenant.
     */
    @Transactional
    public boolean deleteApiKey(UUID id, UUID tenantId) {
        Optional<ApiKey> existing = apiKeyRepository.findByIdAndTenantId(id, tenantId);
        if (existing.isPresent()) {
            apiKeyRepository.deleteByIdAndTenantId(id, tenantId);
            log.info("Deleted API key {} for tenant {}", id, tenantId);
            return true;
        }
        return false;
    }

    /**
     * Helper to convert ApiKey entity to ApiKeyDto.
     */
    private ApiKeyDto toDto(ApiKey apiKey) {
        return ApiKeyDto.builder()
                .id(apiKey.getId())
                .tenantId(apiKey.getTenantId())
                .key(apiKey.getKey())
                .createdAt(apiKey.getCreatedAt())
                .updatedAt(apiKey.getUpdatedAt())
                .build();
    }
}
