package com.susume.recommendation.repository;

import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

        /**
         * Find all interactions for a user within tenant (paginated).
         */
        Page<Interaction> findByTenantIdAndExternalUserId(UUID tenantId, String externalUserId, Pageable pageable);

        /**
         * Find all interactions for a user within tenant (no pagination).
         */
        List<Interaction> findByTenantIdAndExternalUserId(UUID tenantId, String externalUserId);

        /**
         * Find interactions by tenant, user, and type.
         */
        Page<Interaction> findByTenantIdAndExternalUserIdAndInteractionType(
                        UUID tenantId, String externalUserId, InteractionType interactionType, Pageable pageable);

        /**
         * Find interactions by tenant, user, and timestamp after.
         */
        @Query("SELECT i FROM Interaction i WHERE i.tenantId = :tenantId AND i.externalUserId = :externalUserId " +
                        "AND i.timestamp >= :since ORDER BY i.timestamp DESC")
        Page<Interaction> findByTenantIdAndExternalUserIdAndTimestampAfter(
                        @Param("tenantId") UUID tenantId,
                        @Param("externalUserId") String externalUserId,
                        @Param("since") Instant since,
                        Pageable pageable);

        /**
         * Count interactions for an item since a given time (for trending).
         */
        long countByTenantIdAndExternalItemIdAndTimestampAfter(
                        UUID tenantId, String externalItemId, Instant since);

        /**
         * Find trending items for a tenant based on weighted interaction counts.
         *
         * Interaction type weights:
         * - VIEW: 1
         * - CLICK: 2
         * - LIKE: 3
         * - PURCHASE: 5
         *
         * @param tenantId the tenant ID
         * @param since    the timestamp threshold (interactions after this time are
         *                 counted)
         * @param pageable pagination info
         * @return list of Object[] where [0] = externalItemId, [1] = weighted score
         */
        @Query("""
                        SELECT i.externalItemId,
                               SUM(CASE i.interactionType
                                   WHEN 'VIEW' THEN 1
                                   WHEN 'CLICK' THEN 2
                                   WHEN 'LIKE' THEN 3
                                   WHEN 'PURCHASE' THEN 5
                                   ELSE 0
                                   END) as score
                        FROM Interaction i
                        WHERE i.tenantId = :tenantId
                          AND i.timestamp >= :since
                        GROUP BY i.externalItemId
                        ORDER BY score DESC
                        """)
        List<Object[]> findTrendingItemIds(
                        @Param("tenantId") UUID tenantId,
                        @Param("since") Instant since,
                        Pageable pageable);

        Page<Interaction> findByTenantId(UUID tenantId, Pageable pageable);

        List<Interaction> findByTenantId(UUID tenantId);

        long countByTenantId(UUID tenantId);
}
