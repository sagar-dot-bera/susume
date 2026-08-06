package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ItemDetailResponse {
    @JsonProperty("id")
    public final UUID id;

    @JsonProperty("externalItemId")
    public final String externalItemId;

    @JsonProperty("metadata")
    public final Map<String, Object> metadata;

    @JsonProperty("status")
    public final String status;

    @JsonProperty("createdAt")
    public final Instant createdAt;

    @JsonProperty("updatedAt")
    public final Instant updatedAt;

    public ItemDetailResponse(UUID id, String externalItemId, Map<String, Object> metadata,
            String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.externalItemId = externalItemId;
        this.metadata = metadata;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
