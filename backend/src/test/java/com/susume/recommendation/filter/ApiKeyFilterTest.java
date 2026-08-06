package com.susume.recommendation.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.service.TenantService;
import com.susume.recommendation.util.CryptoUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyFilterTest {

    @Mock
    private TenantService tenantService;

    private ApiKeyFilter apiKeyFilter;
    private ObjectMapper objectMapper;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        apiKeyFilter = new ApiKeyFilter(tenantService, objectMapper);
        tenantId = UUID.randomUUID();
    }

    @Test
    void missingApiKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // No API key header
        apiKeyFilter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void invalidApiKeyReturns401() throws ServletException, IOException {
        String invalidApiKey = "invalid-api-key-123";
        String hashedInvalidKey = CryptoUtil.hashAPIKey(invalidApiKey);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        request.addHeader("X-API-KEY", invalidApiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // Tenant not found for invalid key
        when(tenantService.findByApiKeyHash(hashedInvalidKey)).thenReturn(Optional.empty());

        apiKeyFilter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }

    @Test
    void validApiKeySetsSecurityContext() throws ServletException, IOException {
        String validApiKey = "valid-api-key-123";
        String hashedKey = CryptoUtil.hashAPIKey(validApiKey);
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        request.addHeader("X-API-KEY", validApiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(tenantService.findByApiKeyHash(hashedKey)).thenReturn(Optional.of(tenant));

        apiKeyFilter.doFilter(request, response, filterChain);

        // Verify tenant context was set
        assertEquals(tenantId, TenantContext.getTenantId());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void publicEndpointsAreNotFiltered() throws ServletException, IOException {
        MockHttpServletRequest tenantRegRequest = new MockHttpServletRequest("POST", "/api/v1/tenants/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        apiKeyFilter.doFilter(tenantRegRequest, response, filterChain);

        // Should proceed to filter chain without checking API key
        verify(filterChain).doFilter(tenantRegRequest, response);
    }

    @Test
    void emptyApiKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/items");
        request.addHeader("X-API-KEY", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        apiKeyFilter.doFilter(request, response, filterChain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }
}
