package com.susume.recommendation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record InvitationResponse(
                UUID id,
                String email,
                String role,
                String status,
                LocalDateTime expiresAt,
                LocalDateTime acceptedAt,
                LocalDateTime createdAt) {
}
