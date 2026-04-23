package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefreshTokenRequest {
    @JsonProperty("token")
    public String token;

    public RefreshTokenRequest() {
    }

    public RefreshTokenRequest(String token) {
        this.token = token;
    }
}
