package com.susume.recommendation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.susume.recommendation.exception.UserNotFoundException;
import com.susume.recommendation.repository.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthMailService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;

    private final EmailSenderService emailSenderService;
    @Value("${app.base-url}")
    String baseUrl;

    @Value("${app.mail.from}")
    String from;

    @Value("${MAIL_PASSWORD:NOT_FOUND}")
    private String password;

    public AuthMailService(EmailSenderService emailSenderService, RefreshTokenRepository refreshTokenRepository,
            UserService userService) {
        this.emailSenderService = emailSenderService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userService = userService;
    }

    public void sendEmailVerificationToken(String email, String token) {

        if (email == null) {
            log.warn("Email is null, cannot send verification email.");
            throw new IllegalArgumentException("Email cannot be null");
        }

        if (token == null) {
            log.warn("Token is null, cannot send verification email.");
            throw new IllegalArgumentException("Token cannot be null");
        }

        String verifyUrl = baseUrl + "/auth/verify-email?token=" + token;

        log.info("password: {}", password);

        emailSenderService.sendPlainText(email, "Verify your email",
                "Welcome to Susume! Please verify your email by clicking: " + verifyUrl
                        + "\nThis link expires in 1 hours.",
                from);

    }

    public void sendInvitationEmail(String email, String token) {
        if (email == null) {
            log.warn("Email is null, cannot send invitation email.");
            throw new IllegalArgumentException("Email cannot be null");
        }

        if (token == null) {
            log.warn("Token is null, cannot send invitation email.");
            throw new IllegalArgumentException("Token cannot be null");
        }

        String inviteUrl = baseUrl + "/#/invitation/" + token;

        emailSenderService.sendPlainText(email, "You're invited to join Susume",
                "You've been invited to join Susume! Please accept the invitation by clicking: " + inviteUrl
                        + "\nThis link expires in 72 hours.",
                from);
    }

    public void sendEmailResetPasswordToken(String email, String token) {
        if (email == null) {
            log.warn("Email is null, cannot send password reset email.");
            throw new IllegalArgumentException("Email cannot be null");
        }

        if (token == null) {
            log.warn("Token is null, cannot send password reset email.");
            throw new IllegalArgumentException("Token cannot be null");
        }

        if (!userService.doesUserExistByEmail(email)) {
            log.warn("User with email {} does not exist, cannot send password reset email.", email);
            throw new UserNotFoundException(email);
        }

        String verifyUrl = baseUrl + "/auth/reset-password?token=" + token;

        emailSenderService.sendPlainText(email, "Reset your password",
                "Reset your password for Susume Recommendation Platform by clicking: " + verifyUrl
                        + "\nThis link expires in 1 hours.",
                from);
    }

    public void resendEmailVerificationToken(String email, String token) {

        if (email == null) {
            log.warn("RefreshTokenRepository is null, cannot resend verification token.");
            throw new IllegalStateException("RefreshTokenRepository cannot be null");
        }

        if (!userService.doesUserExistByEmail(email)) {
            log.warn("User with email {} does not exist, cannot resend verification token.", email);
            throw new UserNotFoundException(email);
        }

        sendEmailVerificationToken(email, token);
    }
}
