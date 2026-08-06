package com.susume.recommendation.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.susume.recommendation.entity.RecommendationResult;

public interface RecommendationResultRepository extends JpaRepository<RecommendationResult, UUID> {
    long countBySessionId(UUID sessionId);
}
