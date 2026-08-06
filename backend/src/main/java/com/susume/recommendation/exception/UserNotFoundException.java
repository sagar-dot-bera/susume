package com.susume.recommendation.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String username) {
        super("User not found with username/email" + username);
    }
}
