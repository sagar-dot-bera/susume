package com.susume.recommendation.repository;

import com.susume.recommendation.entity.InvitationStatus;
import com.susume.recommendation.entity.TenantInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantInvitationRepository extends JpaRepository<TenantInvitation, UUID> {

    Optional<TenantInvitation> findByToken(String token);

    List<TenantInvitation> findByTenantId(UUID tenantId);

    boolean existsByTenantIdAndEmailAndStatus(UUID tenantId, String email, InvitationStatus status);

    @Modifying
    @Query("DELETE FROM TenantInvitation ti WHERE ti.expiresAt < :now AND ti.status = :status")
    int deleteExpiredInvitations(@Param("now") Instant now, @Param("status") InvitationStatus status);

    @Modifying
    @Query("UPDATE TenantInvitation ti SET ti.status = :expired WHERE ti.expiresAt < :now AND ti.status = :pending")
    int markExpiredInvitations(@Param("now") Instant now,
                               @Param("expired") InvitationStatus expired,
                               @Param("pending") InvitationStatus pending);
}
