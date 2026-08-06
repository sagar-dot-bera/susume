package com.susume.recommendation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.susume.recommendation.entity.UserIdentity;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    boolean existsByUserIdAndProvider(UUID id, String identityType);

    boolean existsByProviderUserIdAndProvider(String providerUserId, String provider);

    Optional<UserIdentity> findByUserIdAndProvider(UUID id, String local);

    Optional<UserIdentity> findByProviderUserIdAndProvider(String providerUserId, String provider);

    List<UserIdentity> findAllByUserId(UUID id);

    void deleteByUserIdAndProvider(UUID id, String provider);

}
