package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(
        String email,
        String password) {

}
