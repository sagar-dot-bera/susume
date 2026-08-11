package com.susume.recommendation.client;

import com.susume.recommendation.dto.RankRequest;
import com.susume.recommendation.dto.RankResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendationRankingClientTest {

    private RestTemplateBuilder restTemplateBuilder;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplateBuilder = mock(RestTemplateBuilder.class);
        restTemplate = mock(RestTemplate.class);
        when(restTemplateBuilder.setConnectTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
    }

    @Test
    void testRankCandidatesSuccess() {
        RecommendationRankingClient client = new RecommendationRankingClient(
                restTemplateBuilder, "http://localhost:8002", true, 500);

        RankResponse expectedResponse = RankResponse.builder()
                .recommendations(List.of(
                        RankResponse.RankedRecommendation.builder().itemId("item-1").score(0.95).build()
                ))
                .model(RankResponse.ModelMetadata.builder().name("susume-ranker").version("v1").build())
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(RankResponse.class)))
                .thenReturn(expectedResponse);

        RankRequest request = RankRequest.builder().limit(5).build();
        Optional<RankResponse> result = client.rankCandidates(request);

        assertTrue(result.isPresent());
        assertEquals("item-1", result.get().getRecommendations().get(0).getItemId());
        assertEquals(0.95, result.get().getRecommendations().get(0).getScore());
    }

    @Test
    void testRankCandidatesFallbackOnError() {
        RecommendationRankingClient client = new RecommendationRankingClient(
                restTemplateBuilder, "http://localhost:8002", true, 500);

        when(restTemplate.postForObject(anyString(), any(), eq(RankResponse.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        RankRequest request = RankRequest.builder().limit(5).build();
        Optional<RankResponse> result = client.rankCandidates(request);

        assertFalse(result.isPresent()); // Graceful fallback
    }

    @Test
    void testDisabledMlRanking() {
        RecommendationRankingClient client = new RecommendationRankingClient(
                restTemplateBuilder, "http://localhost:8002", false, 500);

        Optional<RankResponse> result = client.rankCandidates(RankRequest.builder().build());
        assertFalse(result.isPresent());
        verifyNoInteractions(restTemplate);
    }
}
