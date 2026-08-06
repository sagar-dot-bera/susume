package com.susume.recommendation.repository;

import com.susume.recommendation.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKey(String key);

    List<ApiKey> findByTenantId(UUID tenantId);

    Optional<ApiKey> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndKey(UUID tenantId, String key);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
