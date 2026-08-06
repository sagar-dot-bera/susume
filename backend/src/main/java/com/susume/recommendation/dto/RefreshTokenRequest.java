package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public record RefreshTokenRequest(
        @JsonProperty("token") String token,
        @JsonProperty("id") String id) {

}
