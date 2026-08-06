package com.susume.recommendation.dto;

import java.time.LocalDateTime;

public record InvitationValidationResponse(
        String email,
        String role,
        boolean valid,
        LocalDateTime expiresAt) {
}
