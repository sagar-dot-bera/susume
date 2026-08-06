package com.susume.recommendation.exception;

public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException(String token) {
        super("Invitation not found for token: " + token);
    }

    public InvitationNotFoundException(java.util.UUID id) {
        super("Invitation not found for id: " + id);
    }
}
