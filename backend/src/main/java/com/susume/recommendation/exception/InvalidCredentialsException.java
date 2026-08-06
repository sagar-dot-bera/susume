package com.susume.recommendation.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String id) {
        super("Invalid credentials: " + id);
    }
}
