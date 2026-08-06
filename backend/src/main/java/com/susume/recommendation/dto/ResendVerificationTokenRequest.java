package com.susume.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendVerificationTokenRequest(
        @NotBlank String email) {
}
