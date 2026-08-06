package com.susume.recommendation.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email not verified");
    }

}
