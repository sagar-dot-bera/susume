package com.susume.recommendation.controller;

import com.susume.recommendation.dto.LoginRequest;
import com.susume.recommendation.dto.LoginResponse;
import com.susume.recommendation.dto.RefreshTokenRequest;
import com.susume.recommendation.dto.ErrorResponse;
import com.susume.recommendation.entity.DashboardUser;
import com.susume.recommendation.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Login endpoint.
     * POST /api/v1/auth/login
     * Body: { "email": "...", "password": "..." }
     * Response: { "token": "...", "expiresAt": "..." }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            DashboardUser user = authService.authenticate(request.email, request.password);

            String token = authService.generateToken(user);
            Instant now = Instant.now();
            int jwtExpiryHours = 24; // Default, should match config
            Instant expiresAt = now.plus(jwtExpiryHours, ChronoUnit.HOURS);

            LoginResponse response = new LoginResponse(token, expiresAt);

            log.info("User logged in: {}", user.getId());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("UNAUTHORIZED", "Invalid credentials"));
        } catch (Exception e) {
            log.error("Error during login", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error during login"));
        }
    }

    /**
     * Refresh token endpoint.
     * POST /api/v1/auth/refresh
     * Body: { "token": "..." }
     * Response: { "token": "...", "expiresAt": "..." }
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            java.util.UUID userId = authService.validateAndGetUserId(request.token);
            DashboardUser user = authService.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String newToken = authService.generateToken(user);
            Instant now = Instant.now();
            int jwtExpiryHours = 24; // Default, should match config
            Instant expiresAt = now.plus(jwtExpiryHours, ChronoUnit.HOURS);

            LoginResponse response = new LoginResponse(newToken, expiresAt);

            log.info("Token refreshed for user: {}", userId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("UNAUTHORIZED", "Invalid or expired token"));
        } catch (Exception e) {
            log.error("Error refreshing token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "Error refreshing token"));
        }
    }
}
