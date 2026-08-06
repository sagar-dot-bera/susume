package com.susume.recommendation.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "user_identities")
@AllArgsConstructor
@NoArgsConstructor
public class UserIdentity {

    @Id
    @NonNull
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "provider_user_id", nullable = true)
    private String providerUserId;

    @Column(name = "password_hash", nullable = true)
    private String passwordHash;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

}
