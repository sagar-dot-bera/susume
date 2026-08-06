package com.susume.recommendation.service;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.susume.recommendation.dto.RefreshTokenRequest;
import com.susume.recommendation.entity.RefreshToken;
import com.susume.recommendation.entity.User;
import com.susume.recommendation.exception.RefreshTokenNotFoundException;
import com.susume.recommendation.repository.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserService userService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userService = userService;
    }

    public RefreshToken createRefreshToken(String refreshToken, User user, InetAddress ipAddress, String userAgent) {
        if (refreshToken == null) {
            log.warn("Attempted to create refresh token with null refreshToken");
            throw new IllegalArgumentException("refreshToken cannot be null");
        }

        if (user == null) {
            log.warn("Attempted to create refresh token with null user");
            throw new IllegalArgumentException("user cannot be null");
        }

        if (ipAddress == null) {
            log.warn("Attempted to create refresh token with null ipAddress");
            throw new IllegalArgumentException("ipAddress cannot be null");
        }

        if (userAgent == null) {
            log.warn("Attempted to create refresh token with null userAgent");
            throw new IllegalArgumentException("userAgent cannot be null");
        }

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setToken(HashUtil.hash(refreshToken));
        newRefreshToken.setUser(user);
        newRefreshToken.setRevoked(false);
        newRefreshToken.setCreatedAt(java.time.Instant.now());
        newRefreshToken.setExpiresAt(java.time.Instant.now().plusSeconds(60 * 60 * 24 * 30)); // Set expiration to 7
                                                                                              // days from now
        newRefreshToken.setIpAddress(ipAddress);
        newRefreshToken.setDeviceName(userAgent);
        return refreshTokenRepository.save(newRefreshToken);

    }

    public boolean revokeRefreshToken(RefreshTokenRequest refreshTokenRequest) {
        if (refreshTokenRequest == null) {
            log.warn("Attempted to revoke refresh token with null request");
            throw new IllegalArgumentException("refreshTokenRequest cannot be null");
        }

        RefreshToken refreshToken = refreshTokenRepository.findById(UUID.fromString(refreshTokenRequest.id()))
                .orElseThrow(
                        () -> new RefreshTokenNotFoundException(refreshTokenRequest.token(), refreshTokenRequest.id()));

        if (refreshToken.getRevoked()) {
            log.warn("Attempted to revoke an already revoked refresh token with id: {}", refreshToken.getId());
            return false; // Token is already revoked
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        log.info("Refresh token with id: {} has been revoked", refreshToken.getId());
        return true;

    }

    public boolean isRefreshTokenValid(RefreshTokenRequest refreshTokenRequest) {
        if (refreshTokenRequest == null) {
            log.warn("Attempted to validate refresh token with null request");
            throw new IllegalArgumentException("refreshTokenRequest cannot be null");
        }
        return refreshTokenRepository.existsByTokenAndRevokedFalse(refreshTokenRequest.token());
    }

    public RefreshToken getRefreshToken(String refreshTokenId) {
        return refreshTokenRepository.findById(UUID.fromString(refreshTokenId))
                .orElseThrow(() -> new RefreshTokenNotFoundException(refreshTokenId, refreshTokenId));
    }

    public RefreshToken getValidRefreshToken(String refreshToken) {
        if (refreshToken == null) {
            throw new IllegalArgumentException("refreshToken cannot be null");
        }

        return refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> new RefreshTokenNotFoundException(refreshToken, refreshToken));
    }

    public RefreshToken revokeRefreshToken(String refreshToken) {
        RefreshToken storedRefreshToken = getValidRefreshToken(refreshToken);
        storedRefreshToken.setRevoked(true);
        return refreshTokenRepository.save(storedRefreshToken);
    }

    public void revokeRefreshTokenByIdAndUser(UUID sessionId, User user) {
        if (sessionId == null || user == null) {
            throw new IllegalArgumentException("sessionId/user cannot be null");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new RefreshTokenNotFoundException(sessionId.toString(), sessionId.toString()));
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    public void revokeAllRefreshTokensForUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }

        List<RefreshToken> refreshTokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        for (RefreshToken refreshToken : refreshTokens) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
        }
    }

    public List<RefreshToken> getActiveRefreshTokens(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }

        return refreshTokenRepository.findAllByUserAndRevokedFalse(user);
    }
}
