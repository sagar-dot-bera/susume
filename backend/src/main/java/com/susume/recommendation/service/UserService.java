package com.susume.recommendation.service;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.susume.recommendation.dto.RegisterRequest;
import com.susume.recommendation.dto.UpdateProfileRequest;
import com.susume.recommendation.dto.UserRegistrationRequest;
import com.susume.recommendation.entity.User;
import com.susume.recommendation.entity.UserRole;
import com.susume.recommendation.entity.UserStatus;
import com.susume.recommendation.repository.UserIdentityRepository;
import com.susume.recommendation.repository.UserRepository;
import com.susume.recommendation.exception.EmailAlreadyExistsException;
import com.susume.recommendation.exception.EmailNotVerifiedException;
import com.susume.recommendation.exception.UserNameAlreadyExistsException;
import com.susume.recommendation.exception.UserNotFoundException;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserIdentityRepository userIdentityRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a new user from the provided registration request.
     *
     * @param registerRequest the registration request containing user details
     * @return the created user
     * @throws User
     */
    public User createNewUser(UserRegistrationRequest registerRequest, UUID tenantId, UserRole role) {
        if (registerRequest == null) {
            log.warn("Attempted to create user with null registration request");
            throw new IllegalArgumentException("Registration request cannot be null");
        }

        validateUsernameUniqueness(registerRequest.username());
        validateEmailUniqueness(registerRequest.email());

        final User newUser = new User();
        newUser.setFirstName(registerRequest.firstName());
        newUser.setLastName(registerRequest.lastName());
        newUser.setUsername(registerRequest.username());
        newUser.setEmail(registerRequest.email());
        newUser.setTenantId(tenantId);
        newUser.setRole(role);
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setCreatedAt(Instant.now());
        final User savedUser = userRepository.save(newUser);

        log.info("User created successfully with username: {}", registerRequest.username());
        return savedUser;
    }

    /**
     * Checks if a username is available.
     *
     * @param username the username to check
     * @return true if the username is available, false otherwise
     */
    public boolean isUsernameAvailable(String username) {
        return username != null && !userRepository.existsByUsername(username);
    }

    /**
     * Checks if an email is available.
     *
     * @param email the email to check
     * @return true if the email is available, false otherwise
     */
    public boolean isEmailAvailable(String email) {
        return email != null && !userRepository.existsByEmail(email);
    }

    /**
     * Fetches a user by email address.
     *
     * @param email the email address
     * @return the User object
     */
    public User fetchUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            log.warn("Attempted to fetch user with null or empty email");
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);
                    return new UserNotFoundException(email);
                });
    }

    /**
     * Fetches a user by username.
     *
     * @param username the username
     * @return the User object
     */
    public User fetchUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            log.warn("Attempted to fetch user with null or empty username");
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        return userRepository.findByUsername(username).orElseThrow(
                () -> {
                    log.warn("User not found with username: {}", username);
                    return new UserNotFoundException(username);
                });
    }

    public User fetchUserById(UUID userId) {
        if (userId == null) {
            log.warn("Attempted to fetch user with null id");
            throw new IllegalArgumentException("User ID cannot be null");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found with id: {}", userId);
                    return new UserNotFoundException(userId.toString());
                });
    }

    /**
     * Validates that a username is unique.
     *
     * @param username the username to validate
     * @throws IllegalArgumentException if username is null, empty, or already
     *                                  exists
     */
    private void validateUsernameUniqueness(String username) {
        if (username == null || username.isBlank()) {
            log.warn("Attempted to validate null or empty username");
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        if (!isUsernameAvailable(username)) {
            log.warn("Username already exists: {}", username);
            throw new UserNameAlreadyExistsException(username);
        }
    }

    /**
     * Validates that an email is unique.
     *
     * @param email the email to validate
     * @throws IllegalArgumentException if email is null, empty, or already exists
     */
    private void validateEmailUniqueness(String email) {
        if (email == null || email.isBlank()) {
            log.warn("Attempted to validate null or empty email");
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        if (!isEmailAvailable(email)) {
            log.warn("Email already exists: {}", email);
            throw new EmailAlreadyExistsException(email);
        }
    }

    public boolean doesUserExistById(UUID userId) {
        if (userId == null) {
            log.warn("Attempted to check existence of user with null ID");
            throw new IllegalArgumentException("User ID cannot be null");
        }

        return userRepository.existsById(userId);
    }

    public boolean doesUserExistByEmail(String email) {
        if (email == null || email.isBlank()) {
            log.warn("Attempted to check existence of user with null or empty email");
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        return userRepository.existsByEmail(email);
    }

    public User updateUserProfile(User user, UpdateProfileRequest request) {
        if (user == null || request == null) {
            throw new IllegalArgumentException("User and profile request cannot be null");
        }

        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName());
        }

        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName());
        }

        if (request.username() != null && !request.username().isBlank()
                && !request.username().equals(user.getUsername())) {
            validateUsernameUniqueness(request.username());
            user.setUsername(request.username());
        }

        return userRepository.save(user);
    }

    public User markVerified(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        user.setEmailVerified(true);

        return userRepository.save(user);
    }

    public void isMailVerified(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
    }

    // public User createUserFromGoogleIdPayload(GoogleIdToken.Payload payload) {
    // if (payload == null) {
    // log.warn("Attempted to create user from null Google ID token payload");
    // throw new IllegalArgumentException("Google ID token payload cannot be null");
    // }

    // String email = payload.getEmail();
    // String firstName = (String) payload.get("given_name");
    // String lastName = (String) payload.get("family_name");
    // String username = email.split("@")[0]; // Derive username from email

    // User newUser = new User(firstName, lastName, username, email, true);
    // return userRepository.save(newUser);
    // }

}
