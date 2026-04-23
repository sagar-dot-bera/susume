package com.susume.recommendation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interaction {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "UUID")
    private UUID tenantId;

    @Column(name = "external_user_id", nullable = false, length = 255)
    private String externalUserId;

    @Column(name = "external_item_id", nullable = false, length = 255)
    private String externalItemId;

    @Column(name = "interaction_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InteractionType interactionType;

    @Column(nullable = false)
    private Instant timestamp;

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
