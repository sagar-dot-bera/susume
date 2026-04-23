package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ApiKeyResponse {
    @JsonProperty("maskedKey")
    public final String maskedKey;

    public ApiKeyResponse(String maskedKey) {
        this.maskedKey = maskedKey;
    }
}
