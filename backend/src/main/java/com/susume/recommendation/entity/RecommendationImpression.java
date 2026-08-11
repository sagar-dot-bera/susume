package com.susume.recommendation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "recommendation_impressions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationImpression {

    @Id
    @Column(columnDefinition = "UUID")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, length = 255)
    private String requestId;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "UUID")
    private UUID tenantId;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(name = "item_id", nullable = false, length = 255)
    private String itemId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "strategy_scores", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Double> strategyScores;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
