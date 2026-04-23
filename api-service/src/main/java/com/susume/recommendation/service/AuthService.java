package com.susume.recommendation.service;

import com.susume.recommendation.entity.DashboardUser;
import com.susume.recommendation.repository.DashboardUserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private final DashboardUserRepository dashboardUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiry-hours:24}")
    private int jwtExpiryHours;

    public AuthService(DashboardUserRepository dashboardUserRepository) {
        this.dashboardUserRepository = dashboardUserRepository;
        this.passwordEncoder = new BCryptPasswordEncoder(12); // Cost factor 12
    }

    /**
     * Authenticate user by email and password.
     * Returns the user if credentials are valid, throws exception otherwise.
     */
    @Transactional
    public DashboardUser authenticate(String email, String password) {
        DashboardUser user = dashboardUserRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login attempt with non-existent email: {}", email);
                    return new IllegalArgumentException("Invalid credentials");
                });

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Login failed for user: {} - invalid password", email);
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Update last login timestamp
        user.setLastLoginAt(Instant.now());
        dashboardUserRepository.save(user);

        log.info("User authenticated: {}", email);
        return user;
    }

    /**
     * Generate JWT token for a user.
     */
    public String generateToken(DashboardUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtExpiryHours, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("tenantId", user.getTenantId().toString())
                .claim("role", user.getRole())
                .claim("email", user.getEmail())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Get expiry time for JWT tokens.
     */
    public Instant getTokenExpiryTime(Instant issuedAt) {
        return issuedAt.plus(jwtExpiryHours, ChronoUnit.HOURS);
    }

    /**
     * Validate JWT token and extract user ID.
     */
    public UUID validateAndGetUserId(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return UUID.fromString(claims.getSubject());
        } catch (Exception e) {
            log.warn("Invalid JWT token", e);
            throw new IllegalArgumentException("Invalid token");
        }
    }

    /**
     * Extract claims from JWT token.
     */
    public io.jsonwebtoken.Claims extractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("Failed to extract claims from token", e);
            throw new IllegalArgumentException("Invalid token");
        }
    }

    /**
     * Find user by ID.
     */
    public Optional<DashboardUser> findById(UUID userId) {
        return dashboardUserRepository.findById(userId);
    }
}
