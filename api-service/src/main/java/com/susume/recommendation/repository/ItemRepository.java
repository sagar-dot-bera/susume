package com.susume.recommendation.repository;

import com.susume.recommendation.entity.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {
    Optional<Item> findByTenantIdAndExternalItemId(UUID tenantId, String externalItemId);

    /**
     * Find an active item by tenant and external item ID.
     * Returns null if not found or if item is INACTIVE.
     */
    @Query("SELECT i FROM Item i WHERE i.tenantId = :tenantId AND i.externalItemId = :externalItemId " +
            "AND i.status = 'ACTIVE'")
    Item findActiveByTenantIdAndExternalItemId(
            @Param("tenantId") UUID tenantId,
            @Param("externalItemId") String externalItemId);

    Page<Item> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.tenantId = :tenantId AND i.status = :status " +
            "AND i.createdAt >= :createdAfter ORDER BY i.createdAt ASC")
    Page<Item> findByTenantIdAndStatusAndCreatedAfter(
            @Param("tenantId") UUID tenantId,
            @Param("status") String status,
            @Param("createdAfter") Instant createdAfter,
            Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.tenantId = :tenantId AND i.status = :status " +
            "AND i.createdAt <= :createdBefore ORDER BY i.createdAt ASC")
    Page<Item> findByTenantIdAndStatusAndCreatedBefore(
            @Param("tenantId") UUID tenantId,
            @Param("status") String status,
            @Param("createdBefore") Instant createdBefore,
            Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.tenantId = :tenantId AND i.status = :status " +
            "AND i.createdAt >= :createdAfter AND i.createdAt <= :createdBefore " +
            "ORDER BY i.createdAt ASC")
    Page<Item> findByTenantIdAndStatusAndCreatedBetween(
            @Param("tenantId") UUID tenantId,
            @Param("status") String status,
            @Param("createdAfter") Instant createdAfter,
            @Param("createdBefore") Instant createdBefore,
            Pageable pageable);
}
