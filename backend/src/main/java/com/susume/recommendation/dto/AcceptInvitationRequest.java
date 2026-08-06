package com.susume.recommendation.dto;

public record AcceptInvitationRequest(
        String firstName,
        String lastName,
        String username,
        String password,
        String confirmPassword) {
}
