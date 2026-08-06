package com.susume.recommendation.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.susume.recommendation.entity.UserRole;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    public String generateToken(String userId, String email, String username, UserRole role,
            String tenantId, long expiration) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .claim("email", email)
                .claim("username", username)
                .claim("role", role.toString())
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiresAt(now.plusMillis(expiration))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public String extractEmailFromToken(String token) {
        return jwtDecoder.decode(token).getClaim("email").toString();
    }

    public String extractUserIdFromToken(String token) {
        return jwtDecoder.decode(token).getSubject();
    }

    public String extractTenantIdFromToken(String token) {
        return jwtDecoder.decode(token).getClaim("tenantId").toString();
    }

    public String extractUsernameFromToken(String token) {
        return jwtDecoder.decode(token).getClaim("username").toString();
    }

    public UserRole extractUserRoleFromToken(String token) {
        return UserRole.fromString(jwtDecoder.decode(token).getClaim("role").toString());
    }

    public boolean validateToken(String token, UserDetails user) {
        String usernameFromToken = extractUsernameFromToken(token);
        return (usernameFromToken.equals(user.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Instant expiresAt = jwtDecoder.decode(token).getExpiresAt();
        return expiresAt == null || expiresAt.isBefore(Instant.now());
    }

    public Map<String, Object> extractClaims(String token) {
        Map<String, Object> claimSet = jwtDecoder.decode(token).getClaims();
        return claimSet;
    }
}
