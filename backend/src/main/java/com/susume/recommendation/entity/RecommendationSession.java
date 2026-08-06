package com.susume.recommendation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recommendation_sessions")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RecommendationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = true)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String algorithm;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecommendationResult> results = new ArrayList<>();
}