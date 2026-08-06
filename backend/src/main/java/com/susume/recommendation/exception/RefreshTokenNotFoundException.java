package com.susume.recommendation.exception;

public class RefreshTokenNotFoundException extends RuntimeException {
    public RefreshTokenNotFoundException(String token, String id) {
        super("Refresh token '" + token + " witth id " + id + "not found");
    }

}
