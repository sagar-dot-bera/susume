package com.susume.recommendation.controller;

import com.susume.recommendation.dto.RecommendationRequest;
import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.service.RecommendationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationControllerTest {

    private final RecommendationService recommendationService = mock(RecommendationService.class);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RecommendationController(recommendationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        TenantContext.setTenantId(tenantId);
        when(recommendationService.getRecommendations(any(), any()))
                .thenReturn(new RecommendationResponse(userId, List.of(), "personalized"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void readsUserAndLimitFromQueryParameters() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations")
                        .param("externalUserId", userId.toString())
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("personalized"));

        verify(recommendationService).getRecommendations(new RecommendationRequest(userId, 5), tenantId);
    }

    @Test
    void stillAcceptsDeprecatedJsonBody() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalUserId\":\"" + userId + "\",\"limit\":7}"))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendations(new RecommendationRequest(userId, 7), tenantId);
    }

    @Test
    void rejectsMissingUserId() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(recommendationService, never()).getRecommendations(any(), eq(tenantId));
    }

    @Test
    void rejectsNonUuidUserId() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations").param("externalUserId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
