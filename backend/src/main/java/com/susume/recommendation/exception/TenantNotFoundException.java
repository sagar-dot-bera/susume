package com.susume.recommendation.exception;

public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(String msg) {
        super("Tenant does not exsists");
    }
}
