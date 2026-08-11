package com.susume.recommendation.client;

import com.susume.recommendation.dto.RankRequest;
import com.susume.recommendation.dto.RankResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class RecommendationRankingClient {

    private final RestTemplate restTemplate;
    private final String rankingServiceUrl;
    private final boolean mlRankingEnabled;

    public RecommendationRankingClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${recommendation.ml.service-url:http://localhost:8002}") String rankingServiceUrl,
            @Value("${recommendation.ml.enabled:true}") boolean mlRankingEnabled,
            @Value("${recommendation.ml.timeout-ms:500}") int timeoutMs) {
        this.rankingServiceUrl = rankingServiceUrl;
        this.mlRankingEnabled = mlRankingEnabled;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 200)))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public Optional<RankResponse> rankCandidates(RankRequest request) {
        if (!mlRankingEnabled) {
            log.debug("ML ranking is disabled via configuration.");
            return Optional.empty();
        }

        try {
            String targetUrl = rankingServiceUrl + "/api/v1/recommendations/rank";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<RankRequest> entity = new HttpEntity<>(request, headers);
            RankResponse response = restTemplate.postForObject(targetUrl, entity, RankResponse.class);

            if (response != null && response.getRecommendations() != null) {
                return Optional.of(response);
            }
        } catch (Exception e) {
            log.warn("Failed to invoke Python ML Recommendation Service at {}: {}. Falling back to Spring strategy ordering.",
                    rankingServiceUrl, e.getMessage());
        }

        return Optional.empty();
    }
}
