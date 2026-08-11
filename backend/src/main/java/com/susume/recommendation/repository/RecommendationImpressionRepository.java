package com.susume.recommendation.repository;

import com.susume.recommendation.entity.RecommendationImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecommendationImpressionRepository extends JpaRepository<RecommendationImpression, UUID> {
    List<RecommendationImpression> findByRequestId(String requestId);
    List<RecommendationImpression> findByTenantIdAndUserId(UUID tenantId, String userId);
}
