package com.susume.recommendation.client;

import com.susume.recommendation.dto.EmbedRequest;
import com.susume.recommendation.dto.EmbedResponse;
import com.susume.recommendation.exception.EmbeddingServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class EmbeddingServiceClient {

    private final RestTemplate restTemplate;

    @Value("${embedding.service.url:http://localhost:8001}")
    private String embeddingServiceUrl;

    public EmbeddingServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return response.getStatusCode().is4xxClientError() ||
                        response.getStatusCode().is5xxServerError();
            }

            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                log.error("Embedding service error: {}", response.getStatusCode());
            }
        });
    }

    /**
     * Call the embedding service to get embeddings for the given text.
     * Timeout: 2 seconds. On timeout/error: throw EmbeddingServiceException
     * 
     * @param text The text to embed
     * @return float array of embeddings
     */
    public float[] getEmbedding(String text) {
        try {
            EmbedRequest request = new EmbedRequest(text);
            String url = embeddingServiceUrl + "/embed";

            EmbedResponse response = restTemplate.postForObject(url, request, EmbedResponse.class);

            if (response == null || response.embedding == null) {
                throw new EmbeddingServiceException("Empty embedding response from service");
            }

            // Convert List<Float> to float[]
            float[] result = new float[response.embedding.size()];
            for (int i = 0; i < response.embedding.size(); i++) {
                result[i] = response.embedding.get(i);
            }

            log.debug("Embedding generated with dimension: {}", result.length);
            return result;

        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Embedding service timeout or connection error", e);
            throw new EmbeddingServiceException("Embedding service unavailable", e);
        } catch (Exception e) {
            log.error("Error calling embedding service", e);
            throw new EmbeddingServiceException("Error calling embedding service", e);
        }
    }
}
