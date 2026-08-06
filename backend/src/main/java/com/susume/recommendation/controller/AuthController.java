package com.susume.recommendation.controller;

import com.susume.recommendation.dto.LoginRequest;
import com.susume.recommendation.dto.NewAdminAccountRequest;
import com.susume.recommendation.dto.RefreshTokenRequest;
import com.susume.recommendation.dto.ResendVerificationTokenRequest;
import com.susume.recommendation.dto.ResetPasswordRequest;
import com.susume.recommendation.dto.AuthMessageResponse;
import com.susume.recommendation.dto.AuthResponse;
import com.susume.recommendation.dto.ForgotPasswordRequest;
import com.susume.recommendation.entity.User;
import com.susume.recommendation.filter.JwtContext;
import com.susume.recommendation.service.AuthService;
import com.susume.recommendation.service.UserService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /**
     * Register a new dashboard user under an existing tenant.
     * POST /api/v1/auth/register
     */
    @PostMapping("/register-admin")
    public ResponseEntity<AuthMessageResponse> register(@Valid @RequestBody NewAdminAccountRequest request) {
        authService.registerUser(request);
        return ResponseEntity.ok(new AuthMessageResponse("Registration complete"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) throws UnknownHostException {
        return ResponseEntity
                .ok(authService.loginUser(request, getClientIp(httpServletRequest), getUserAgent(httpServletRequest)));
    }

    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest request) throws UnknownHostException {
        return ResponseEntity.ok(
                authService.refreshTokens(refreshTokenRequest.token(), getClientIp(request), getUserAgent(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        authService.logout(refreshTokenRequest.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        UUID userId = JwtContext.getUserId();
        User user = userService.fetchUserById(userId);
        authService.logoutAll(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthMessageResponse> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new AuthMessageResponse("Email verified"));
    }

    public ResponseEntity<AuthMessageResponse> verifyEmailPath(@PathVariable String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new AuthMessageResponse("Email verified"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<AuthMessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationTokenRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(new AuthMessageResponse("Verification email sent"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthMessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(new AuthMessageResponse("Password reset email sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthMessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new AuthMessageResponse("Password reset successful"));
    }

    private InetAddress getClientIp(HttpServletRequest request) throws UnknownHostException {
        return InetAddress.getByName(request.getRemoteAddr());
    }

    private String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null ? "unknown" : userAgent;
    }
}
