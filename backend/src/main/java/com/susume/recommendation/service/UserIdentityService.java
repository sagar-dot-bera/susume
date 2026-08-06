package com.susume.recommendation.service;

import java.security.AuthProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.susume.recommendation.entity.AuthProviders;
import com.susume.recommendation.entity.User;
import com.susume.recommendation.entity.UserIdentity;
import com.susume.recommendation.exception.InvalidCredentialsException;
import com.susume.recommendation.exception.UserIdentityAlreadyExistsException;
import com.susume.recommendation.repository.UserIdentityRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserIdentityService {

    private final UserIdentityRepository userIdentityRepository;
    private final PasswordEncoder passwordEncoder;

    public UserIdentityService(UserIdentityRepository userIdentityRepository, PasswordEncoder passwordEncoder) {
        this.userIdentityRepository = userIdentityRepository;
        this.passwordEncoder = passwordEncoder;

    }

    public UserIdentity createLocalIdentity(User user, String rawPassword) {

        if (rawPassword == null || user == null) {
            log.warn("Attempted to create local user identity with null password/user");
            throw new IllegalArgumentException("password/user name cannot be null");
        }

        if (identityExistsAlready(user, AuthProviders.LOCAL)) {
            log.warn("UserIdentity Already exists for user" + user.getId());
            throw new UserIdentityAlreadyExistsException(user.getUsername(), AuthProviders.LOCAL);
        }

        UserIdentity userIdentity = new UserIdentity();
        String hashedPassword = passwordEncoder.encode(rawPassword);
        userIdentity.setUser(user);
        userIdentity.setPasswordHash(hashedPassword);
        userIdentity.setProvider(AuthProviders.LOCAL);
        userIdentity.setCreatedAt(Instant.now());

        UserIdentity savedUserIdentity = userIdentityRepository.save(userIdentity);

        return savedUserIdentity;
    }

    public boolean identityExistsAlready(User user, String identityType) {
        return userIdentityRepository.existsByUserIdAndProvider(user.getId(), identityType);
    }

    // public UserIdentity createGooglUserIdentity(User user, String providerId) {

    // if (providerId == null || user == null) {
    // log.warn("Attempted to create google user identity with null
    // providerId/user");
    // throw new IllegalArgumentException("providerid/user cannot be null");
    // }

    // if (identityExistsAlready(user, AuthProviders.GOOGLE)) {
    // log.warn("UserIdentity Already exists for user" + user.getId());
    // throw new UserIdentityAlreadyExistsException(user.getEmail(),
    // AuthProviders.GOOGLE);
    // }

    // UserIdentity userIdentity = new UserIdentity();
    // userIdentity.setUser(user);
    // userIdentity.setProvider(AuthProviders.GOOGLE);
    // userIdentity.setProviderUserId(providerId);
    // userIdentity.setCreatedAt(Instant.now());

    // UserIdentity savedUserIdentity = userIdentityRepository.save(userIdentity);

    // return savedUserIdentity;
    // }

    public void validateLocalIdentity(User user, String rawPassword) {
        if (user == null || rawPassword == null) {
            log.warn("Attempted to validate local identity with null user/password");
            throw new IllegalArgumentException("user/password cannot be null");
        }

        Optional<UserIdentity> optionalUserIdentity = userIdentityRepository
                .findByUserIdAndProvider(user.getId(), AuthProviders.LOCAL);

        if (optionalUserIdentity.isEmpty()) {
            log.warn("No local identity found for user: {}", user.getId());

            throw new InvalidCredentialsException(user.getEmail().toString());
        }

        UserIdentity userIdentity = optionalUserIdentity.get();
        if (!passwordEncoder.matches(rawPassword, userIdentity.getPasswordHash())) {
            log.warn("Password mismatch for user: {}", user.getId());
            throw new InvalidCredentialsException(user.getEmail().toString());
        }
    }

    public boolean doesGoogleIdentityExist(String providerId) {
        if (providerId == null) {
            log.warn("Attempted to check google identity existence with null providerId");
            throw new IllegalArgumentException("providerId cannot be null");
        }

        return userIdentityRepository.existsByProviderUserIdAndProvider(providerId, AuthProviders.GOOGLE);
    }

    public User getUserByProviderId(String providerId, String provider) {
        if (providerId == null || provider == null) {
            log.warn("Attempted to get user by providerId or provider with null values");
            throw new IllegalArgumentException("providerId/provider cannot be null");
        }

        Optional<UserIdentity> optionalUserIdentity = userIdentityRepository
                .findByProviderUserIdAndProvider(providerId, provider);

        if (optionalUserIdentity.isEmpty()) {
            log.warn("No user identity found for providerId: {} and provider: {}", providerId, provider);
            throw new InvalidCredentialsException(providerId);
        }

        return optionalUserIdentity.get().getUser();
    }

    public List<UserIdentity> getIdentitiesForUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }

        return userIdentityRepository.findAllByUserId(user.getId());
    }

    // public UserIdentity linkGoogleIdentity(User user, String providerId) {
    //     if (user == null || providerId == null) {
    //         throw new IllegalArgumentException("user/providerId cannot be null");
    //     }

    //     Optional<UserIdentity> existingIdentity = userIdentityRepository.findByProviderUserIdAndProvider(providerId,
    //             AuthProviders.GOOGLE);
    //     if (existingIdentity.isPresent()) {
    //         UserIdentity userIdentity = existingIdentity.get();
    //         userIdentity.setUser(user);
    //         return userIdentityRepository.save(userIdentity);
    //     }

    //     return createGooglUserIdentity(user, providerId);
    // }

    public void unlinkGoogleIdentity(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }

        userIdentityRepository.deleteByUserIdAndProvider(user.getId(), AuthProviders.GOOGLE);
    }

    public void changeLocalPassword(User user, String currentPassword, String newPassword) {
        if (user == null || currentPassword == null || newPassword == null) {
            throw new IllegalArgumentException("user/currentPassword/newPassword cannot be null");
        }

        Optional<UserIdentity> optionalUserIdentity = userIdentityRepository.findByUserIdAndProvider(user.getId(),
                AuthProviders.LOCAL);
        if (optionalUserIdentity.isEmpty()) {
            throw new InvalidCredentialsException(user.getEmail().toString());
        }

        UserIdentity userIdentity = optionalUserIdentity.get();
        if (!passwordEncoder.matches(currentPassword, userIdentity.getPasswordHash())) {
            throw new InvalidCredentialsException(user.getEmail().toString());
        }

        userIdentity.setPasswordHash(passwordEncoder.encode(newPassword));
        userIdentityRepository.save(userIdentity);
    }

    public void resetLocalPassword(User user, String newPassword) {
        if (user == null || newPassword == null) {
            throw new IllegalArgumentException("user/newPassword cannot be null");
        }

        Optional<UserIdentity> optionalUserIdentity = userIdentityRepository.findByUserIdAndProvider(user.getId(),
                AuthProviders.LOCAL);
        if (optionalUserIdentity.isEmpty()) {
            throw new InvalidCredentialsException(user.getEmail().toString());
        }

        UserIdentity userIdentity = optionalUserIdentity.get();
        userIdentity.setPasswordHash(passwordEncoder.encode(newPassword));
        userIdentityRepository.save(userIdentity);
    }

}
