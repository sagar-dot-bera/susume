package com.susume.recommendation.dto;

public record CreateInvitationRequest(
        String email,
        String role) {
}