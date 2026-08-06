package com.susume.recommendation.service;

import org.springframework.stereotype.Service;

import com.susume.recommendation.entity.AuthToken;
import com.susume.recommendation.entity.User;
import com.susume.recommendation.repository.AuthTokenRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthTokenService {
    private final AuthTokenRepository authTokenRepository;
    private final JwtService jwtService;

    public AuthTokenService(AuthTokenRepository authTokenRepository, JwtService jwtService) {
        this.authTokenRepository = authTokenRepository;
        this.jwtService = jwtService;
    }

    public AuthToken saveToken(User user, String token, long expiration, String tokenType) {

        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }

        AuthToken authToken = new AuthToken();
        authToken.setUser(user);
        authToken.setTokenHash(HashUtil.hash(token));
        authToken.setType(tokenType);
        authToken.setExpiresAt(java.time.Instant.now().plusSeconds(expiration));
        authToken.setCreatedAt(java.time.Instant.now());

        authTokenRepository.save(authToken);

        return authToken;
    }

    public boolean validateAuthToken(String token, String tokenType) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        if (tokenType == null) {
            throw new IllegalArgumentException("Token type cannot be null");
        }

        String hashedToken = HashUtil.hash(token);
        AuthToken authToken = authTokenRepository.findByTokenHash(hashedToken);

        if (authToken == null) {
            log.warn("Auth token not found for token: {} and type: {}", token, tokenType);
            return false;
        }

        if (authToken.getExpiresAt().isBefore(java.time.Instant.now())) {
            log.warn("Auth token expired for token: {} and type: {}", token, tokenType);
            return false;
        }

        return true;
    }

    public void deleteAuthToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }

        String hashedToken = HashUtil.hash(token);
        AuthToken authToken = authTokenRepository.findByTokenHash(hashedToken);

        if (authToken != null) {
            authTokenRepository.delete(authToken);
            log.info("Auth token deleted for token: {}", token);
        } else {
            log.warn("Auth token not found for deletion for token: {}", token);
        }
    }

    public void deleteAuthToken(String token, String tokenType) {
        if (token == null || tokenType == null) {
            throw new IllegalArgumentException("Token and token type cannot be null");
        }

        String hashedToken = HashUtil.hash(token);
        AuthToken authToken = authTokenRepository.findByTokenHashAndType(hashedToken, tokenType);

        if (authToken != null) {
            authTokenRepository.delete(authToken);
            log.info("Auth token deleted for token: {} and type: {}", token, tokenType);
        }
    }

    public AuthToken getAuthToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }

        String hashedToken = HashUtil.hash(token);
        AuthToken authToken = authTokenRepository.findByTokenHash(hashedToken);

        if (authToken == null) {
            log.warn("Auth token not found for token: {}", token);
            return null;
        }

        return authToken;
    }

    public AuthToken getAuthToken(String token, String tokenType) {
        if (token == null || tokenType == null) {
            throw new IllegalArgumentException("Token and token type cannot be null");
        }

        String hashedToken = HashUtil.hash(token);
        return authTokenRepository.findByTokenHashAndType(hashedToken, tokenType);
    }

}
