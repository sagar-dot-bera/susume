package com.susume.recommendation.exception;

public class FailedToFindItemException extends RuntimeException {

    public FailedToFindItemException(String id) {
        super("Failed to find item by id:" + id);
    }
}
