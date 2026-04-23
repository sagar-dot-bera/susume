package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public class TenantRegistrationResponse {
    @JsonProperty("tenantId")
    public final UUID tenantId;

    @JsonProperty("apiKey")
    public final String apiKey;

    @JsonProperty("name")
    public final String name;

    @JsonProperty("createdAt")
    public final Instant createdAt;

    public TenantRegistrationResponse(UUID tenantId, String apiKey, String name, Instant createdAt) {
        this.tenantId = tenantId;
        this.apiKey = apiKey;
        this.name = name;
        this.createdAt = createdAt;
    }
}
