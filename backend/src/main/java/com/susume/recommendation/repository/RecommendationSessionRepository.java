package com.susume.recommendation.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.susume.recommendation.dto.RecommendationOverTimeProjection;
import com.susume.recommendation.entity.RecommendationSession;

import org.springframework.data.repository.query.Param;

public interface RecommendationSessionRepository extends JpaRepository<RecommendationSession, UUID> {

    long countByTenantId(UUID tenantId);

    @Query("SELECT COALESCE(AVG(rs.latencyMs), 0) FROM RecommendationSession rs WHERE rs.tenantId = :tenantId")
    int findAverageLatencyByTenantId(@Param("tenantId") UUID tenantId);

    @Query(value = """
            SELECT
                date_trunc('hour', generated_at) AS hour,
                COUNT(*) AS count
            FROM recommendation_sessions
            WHERE tenant_id = :tenantId
              AND generated_at >= NOW() - INTERVAL '7 hours'
            GROUP BY hour
            ORDER BY hour
            """, nativeQuery = true)
    List<RecommendationOverTimeProjection> getRecommendationCountsLast7Hours(@Param("tenantId") UUID tenantId);

    @Query(value = """
            SELECT id FROM recommendation_sessions WHERE tenant_id = :tenantId
            """, nativeQuery = true)
    List<UUID> findDistinctByTenantId(@Param("tenantId") UUID tenantId);

}
