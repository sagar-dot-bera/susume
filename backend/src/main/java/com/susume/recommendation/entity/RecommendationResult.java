package com.susume.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "recommendation_results", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "session_id", "rank" }),
        @UniqueConstraint(columnNames = { "session_id", "item_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private RecommendationSession session;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private Integer rank;
}