package com.susume.recommendation.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.susume.recommendation.dto.ErrorResponse;
import com.susume.recommendation.entity.UserRole;
import com.susume.recommendation.service.AuthService;
import com.susume.recommendation.service.JwtService;
import com.susume.recommendation.filter.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtFilter(JwtService authService, ObjectMapper objectMapper) {
        this.jwtService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Apply filter only to JWT-protected routes
        if (shouldApplyFilter(requestPath)) {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendUnauthorized(response, "Missing or invalid Authorization header");
                return;
            }

            String token = authHeader.substring(7);

            try {
                UUID userId = UUID.fromString(jwtService.extractUserIdFromToken(token));
                UUID tenantId = UUID.fromString(jwtService.extractTenantIdFromToken(token));
                UserRole role = jwtService.extractUserRoleFromToken(token);

                // Set context for downstream use
                JwtContext.setUserId(userId);
                JwtContext.setTenantId(tenantId);
                JwtContext.setRole(role);
                TenantContext.setTenantId(tenantId);

                // Set Spring Security Authentication so .authenticated() authorization passes
                List<SimpleGrantedAuthority> authorities = role != null 
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role.name())) 
                        : Collections.emptyList();
                UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT validated for user: {}", userId);
            } catch (Exception e) {
                log.warn("JWT validation failed", e);
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            JwtContext.clear();
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean shouldApplyFilter(String requestPath) {
        return requestPath.startsWith("/api/v1/dashboard/") ||
                requestPath.startsWith("/api/v1/auth/refresh") ||
                requestPath.startsWith("/api/v1/api-keys");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = new ErrorResponse("UNAUTHORIZED", message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Don't filter public endpoints
        return path.startsWith("/api/v1/tenants/") ||
                path.startsWith("/api/v1/auth/login") ||
                path.startsWith("/api/v1/items/") ||
                path.startsWith("/api/v1/interactions/") ||
                path.startsWith("/api/v1/recommendations/");
    }
}
