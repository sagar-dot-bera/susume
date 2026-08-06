package com.susume.recommendation.entity;

public class AuthTokenTypes {
    public static final String ACCESS = "ACCESS";
    public static final String REFRESH = "REFRESH";
    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    private AuthTokenTypes() {
        // Private constructor to prevent instantiation
    }
}
