package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponse {
    @JsonProperty("code")
    public final String code;

    @JsonProperty("message")
    public final String message;

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
