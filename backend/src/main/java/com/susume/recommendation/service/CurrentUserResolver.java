package com.susume.recommendation.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.entity.User;

@Service
public class CurrentUserResolver {

    private final JwtService jwtService;
    private final UserService userService;
    private final TenantService tenantService;

    public CurrentUserResolver(JwtService jwtService, UserService userService, TenantService tenantService) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.tenantService = tenantService;
    }

    public User resolveUser(String authorizationHeader) {
        String token = resolveBearerToken(authorizationHeader);
        String userId = jwtService.extractUserIdFromToken(token);
        return userService.fetchUserById(UUID.fromString(userId));
    }

    public Tenant resolveTenant(String authoriationHeader) {
        String token = resolveBearerToken(authoriationHeader);
        String tenantId = jwtService.extractTenantIdFromToken(token);
        return tenantService.featchTenant(UUID.fromString(tenantId));
    }

    public String resolveBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }

        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header must use Bearer scheme");
        }

        return authorizationHeader.substring(7).trim();
    }

}
