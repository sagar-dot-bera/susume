package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class LoginResponse {
    @JsonProperty("token")
    public final String token;

    @JsonProperty("expiresAt")
    public final Instant expiresAt;

    public LoginResponse(String token, Instant expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }
}
