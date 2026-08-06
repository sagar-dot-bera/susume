package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public class ItemResponse {
    @JsonProperty("id")
    public final UUID id;

    @JsonProperty("externalItemId")
    public final String externalItemId;

    @JsonProperty("status")
    public final String status;

    @JsonProperty("createdAt")
    public final Instant createdAt;

    @JsonProperty("updatedAt")
    public final Instant updatedAt;

    public ItemResponse(UUID id, String externalItemId, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.externalItemId = externalItemId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
