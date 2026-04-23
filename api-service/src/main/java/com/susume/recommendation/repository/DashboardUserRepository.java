package com.susume.recommendation.repository;

import com.susume.recommendation.entity.DashboardUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardUserRepository extends JpaRepository<DashboardUser, UUID> {
    Optional<DashboardUser> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<DashboardUser> findByEmail(String email);
}
