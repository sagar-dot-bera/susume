package com.susume.recommendation.controller;

import com.susume.recommendation.dto.InteractionHistoryItemResponse;
import com.susume.recommendation.entity.Interaction;
import com.susume.recommendation.entity.InteractionType;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.service.InteractionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InteractionControllerTest {

    private final InteractionService interactionService = mock(InteractionService.class);
    private final UUID tenantId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InteractionController(interactionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void historyUsesQueryParametersAndTheApiKeysTenant() throws Exception {
        Interaction interaction = Interaction.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).externalUserId("user-1").externalItemId("item-1")
                .interactionType(InteractionType.CLICK).timestamp(Instant.parse("2026-01-02T00:00:00Z")).build();
        when(interactionService.getUserInteractions(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(interaction), PageRequest.of(1, 1), 3));
        when(interactionService.toHistoryItemResponse(interaction)).thenReturn(new InteractionHistoryItemResponse(
                interaction.getId(), "item-1", "CLICK", interaction.getTimestamp()));

        // A tenantId smuggled into the request must be ignored.
        mockMvc.perform(get("/api/v1/interactions/history")
                        .param("externalUserId", "user-1")
                        .param("interactionType", "CLICK")
                        .param("since", "2026-01-01T00:00:00Z")
                        .param("page", "1")
                        .param("limit", "1")
                        .param("tenantId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].externalItemId").value("item-1"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(interactionService).getUserInteractions(
                tenantId, "user-1", "CLICK", Instant.parse("2026-01-01T00:00:00Z"), 1, 1);
    }

    @Test
    void historyKeepsTrailingSlashPath() throws Exception {
        when(interactionService.getUserInteractions(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/interactions/history/").param("externalUserId", "user-1"))
                .andExpect(status().isOk());

        verify(interactionService).getUserInteractions(tenantId, "user-1", null, null, 0, 20);
    }

    @Test
    void historyRequiresUserId() throws Exception {
        mockMvc.perform(get("/api/v1/interactions/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void historyRejectsMalformedSince() throws Exception {
        mockMvc.perform(get("/api/v1/interactions/history")
                        .param("externalUserId", "user-1")
                        .param("since", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void historyReportsInvalidArgumentsAsBadRequest() throws Exception {
        when(interactionService.getUserInteractions(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("Invalid interaction type: TELEPORT"));

        mockMvc.perform(get("/api/v1/interactions/history")
                        .param("externalUserId", "user-1")
                        .param("interactionType", "TELEPORT"))
                .andExpect(status().isBadRequest());
    }
}
