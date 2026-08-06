package com.susume.recommendation.service;

import com.susume.recommendation.entity.Tenant;

/**
 * Data holder for tenant registration that includes the raw API key.
 * This should be used only internally and the raw key should never be logged.
 */
public class TenantRegistrationData {
    public final Tenant tenant;
    public final String rawApiKey;

    public TenantRegistrationData(Tenant tenant, String rawApiKey) {
        this.tenant = tenant;
        this.rawApiKey = rawApiKey;
    }
}
