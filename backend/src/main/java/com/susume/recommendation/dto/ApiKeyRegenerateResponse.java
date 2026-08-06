package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ApiKeyRegenerateResponse {
    @JsonProperty("apiKey")
    public final String apiKey;

    public ApiKeyRegenerateResponse(String apiKey) {
        this.apiKey = apiKey;
    }
}
