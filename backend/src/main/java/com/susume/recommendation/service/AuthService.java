package com.susume.recommendation.service;

import com.susume.recommendation.dto.AuthResponse;
import com.susume.recommendation.dto.LoginRequest;
import com.susume.recommendation.dto.NewAdminAccountRequest;
import com.susume.recommendation.dto.ResetPasswordRequest;
import com.susume.recommendation.entity.AuthToken;
import com.susume.recommendation.entity.AuthTokenTypes;
import com.susume.recommendation.entity.RefreshToken;
import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.entity.User;
import com.susume.recommendation.entity.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.InetAddress;

@Service
public class AuthService {
    UserService userService;
    JwtService jwtService;
    AuthTokenService authTokenService;
    AuthMailService authMailService;
    RefreshTokenService refreshTokenService;
    UserIdentityService userIdentityService;
    TenantService tenantService;

    public AuthService(UserService userService, JwtService jwtService, AuthTokenService authTokenService,
            AuthMailService authMailService, RefreshTokenService refreshTokenService,
            UserIdentityService userIdentityService, TenantService tenantService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.authTokenService = authTokenService;
        this.authMailService = authMailService;
        this.refreshTokenService = refreshTokenService;
        this.tenantService = tenantService;
        this.userIdentityService = userIdentityService;
    }

    @Transactional
    public void registerUser(NewAdminAccountRequest registerRequest) {
        Tenant tenant = tenantService.createTenant(registerRequest.tenantRegistrationRequest(),
                registerRequest.userRegistrationRequest().email());
        User newUser = userService.createNewUser(registerRequest.userRegistrationRequest(), tenant.getId(),
                UserRole.ADMIN);

        String token = jwtService.generateToken(
                newUser.getId().toString(),
                newUser.getEmail(),
                newUser.getUsername(),
                newUser.getRole(),
                newUser.getTenantId().toString(),
                3600000L);

        authTokenService.saveToken(
                newUser,
                token,
                3600000L,
                AuthTokenTypes.EMAIL_VERIFICATION);

        userIdentityService.createLocalIdentity(newUser, registerRequest.userRegistrationRequest().password());

        authMailService.sendEmailVerificationToken(newUser.getEmail(), token);

    }

    public AuthResponse loginUser(LoginRequest loginRequest, InetAddress ipAddress, String userAgent) {
        User user = userService.fetchUserByEmail(loginRequest.email());

        userService.isMailVerified(user); // Check if the user's email is verified

        userIdentityService.validateLocalIdentity(user, loginRequest.password());

        AuthResponse authResponse = issueToken(user, ipAddress, userAgent);

        return authResponse;
    }

    // public AuthResponse loginWithGoogle(GoogleLoginRequest request, InetAddress
    // ipAddress, String userAgent)
    // throws GoogleIdTokenNotValidException, GeneralSecurityException, IOException
    // {
    // GoogleIdToken.Payload idTokenPayload =
    // googleTokenVerifier.verifyToken(request.token_id());

    // if
    // (!userIdentityService.doesGoogleIdentityExist(idTokenPayload.getSubject())) {
    // // User does not exist, create a new user
    // User newUser = userService.createUserFromGoogleIdPayload(idTokenPayload);
    // userIdentityService.createGooglUserIdentity(newUser,
    // idTokenPayload.getSubject());
    // return issueToken(newUser, ipAddress, userAgent);
    // }

    // User existingUser =
    // userIdentityService.getUserByProviderId(idTokenPayload.getSubject(),
    // AuthProviders.GOOGLE);
    // return issueToken(existingUser, ipAddress, userAgent);
    // }

    public void verifyEmail(String token) {
        AuthToken authToken = authTokenService.getAuthToken(token, AuthTokenTypes.EMAIL_VERIFICATION);
        if (authToken == null) {
            throw new IllegalArgumentException("Verification token is invalid or expired");
        }

        userService.markVerified(authToken.getUser());
        authTokenService.deleteAuthToken(token, AuthTokenTypes.EMAIL_VERIFICATION);
    }

    public void resendVerification(String email) {
        User user = userService.fetchUserByEmail(email);
        if (user.isEmailVerified()) {
            return;
        }

        String token = jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getTenantId().toString(),
                3600000L);

        authTokenService.saveToken(
                user,
                token,
                3600000L,
                AuthTokenTypes.EMAIL_VERIFICATION);

        authMailService.sendEmailVerificationToken(user.getEmail(), token);
    }

    public void forgotPassword(String email) {
        User user = userService.fetchUserByEmail(email);

        String token = jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getTenantId().toString(),
                3600000L);

        authTokenService.saveToken(user, token, 3600000L, AuthTokenTypes.PASSWORD_RESET);
        authMailService.sendEmailResetPasswordToken(user.getEmail(), token);
    }

    public void resetPassword(ResetPasswordRequest request) {
        AuthToken authToken = authTokenService.getAuthToken(request.token(), AuthTokenTypes.PASSWORD_RESET);
        if (authToken == null) {
            throw new IllegalArgumentException("Password reset token is invalid or expired");
        }

        userIdentityService.resetLocalPassword(authToken.getUser(), request.newPassword());
        authTokenService.deleteAuthToken(request.token(), AuthTokenTypes.PASSWORD_RESET);
    }

    public AuthResponse refreshTokens(String refreshToken, InetAddress ipAddress, String userAgent) {
        RefreshToken storedRefreshToken = refreshTokenService.revokeRefreshToken(refreshToken);
        return issueToken(storedRefreshToken.getUser(), ipAddress, userAgent);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revokeRefreshToken(refreshToken);
    }

    public void logoutAll(User user) {
        refreshTokenService.revokeAllRefreshTokensForUser(user);
    }

    private AuthResponse issueToken(User user, InetAddress ipAddress, String userAgent) {
        String accessToken = jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getTenantId().toString(),
                3600000L);

        String refreshToken = jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getTenantId().toString(),
                604800000L);

        refreshTokenService.createRefreshToken(refreshToken, user, ipAddress, userAgent);

        return new AuthResponse(accessToken, refreshToken);
    }
}
