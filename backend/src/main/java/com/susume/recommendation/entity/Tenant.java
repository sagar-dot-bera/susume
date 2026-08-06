package com.susume.recommendation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @Column(columnDefinition = "UUID")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "slug", nullable = false)
    private String slug;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }
}
