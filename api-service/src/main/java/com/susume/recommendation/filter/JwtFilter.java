package com.susume.recommendation.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.susume.recommendation.dto.ErrorResponse;
import com.susume.recommendation.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public JwtFilter(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
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
                Claims claims = authService.extractClaims(token);

                UUID userId = UUID.fromString(claims.getSubject());
                UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
                String role = claims.get("role", String.class);

                // Set context for downstream use
                JwtContext.setUserId(userId);
                JwtContext.setTenantId(tenantId);
                JwtContext.setRole(role);

                log.debug("JWT validated for user: {}", userId);
            } catch (Exception e) {
                log.warn("JWT validation failed", e);
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldApplyFilter(String requestPath) {
        return requestPath.startsWith("/api/v1/dashboard/") ||
                requestPath.startsWith("/api/v1/auth/refresh");
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
