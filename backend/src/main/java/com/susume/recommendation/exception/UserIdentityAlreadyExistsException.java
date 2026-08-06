package com.susume.recommendation.exception;

public class UserIdentityAlreadyExistsException extends RuntimeException {

    public UserIdentityAlreadyExistsException(String userId, String idType) {
        super(idType + "User Identity already exists with user name" + userId);
    }
}
