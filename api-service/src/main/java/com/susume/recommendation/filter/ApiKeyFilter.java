package com.susume.recommendation.filter;

import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.service.TenantService;
import com.susume.recommendation.util.CryptoUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.susume.recommendation.dto.ErrorResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class ApiKeyFilter extends OncePerRequestFilter {

    private final TenantService tenantService;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(TenantService tenantService, ObjectMapper objectMapper) {
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Apply filter only to API key-protected routes
        if (shouldApplyFilter(requestPath)) {
            String apiKey = request.getHeader("X-API-KEY");

            if (apiKey == null || apiKey.isEmpty()) {
                sendUnauthorized(response, "Invalid API key");
                return;
            }

            // Hash the provided API key
            String hashedApiKey = CryptoUtil.hashAPIKey(apiKey);

            // Find tenant by hashed API key
            Optional<Tenant> tenantOpt = tenantService.findByApiKeyHash(hashedApiKey);

            if (tenantOpt.isEmpty()) {
                log.warn("Invalid API key provided");
                sendUnauthorized(response, "Invalid API key");
                return;
            }

            Tenant tenant = tenantOpt.get();

            // Set tenant context (store tenant ID for downstream use)
            TenantContext.setTenantId(tenant.getId());

            log.debug("Tenant authenticated via API key: {}", tenant.getId());
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldApplyFilter(String requestPath) {
        return requestPath.startsWith("/api/v1/items") ||
                requestPath.startsWith("/api/v1/interactions") ||
                requestPath.startsWith("/api/v1/recommendations");
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
        return path.startsWith("/api/v1/tenants/register") ||
                path.startsWith("/api/v1/auth/") ||
                path.startsWith("/api/v1/dashboard/");
    }
}
