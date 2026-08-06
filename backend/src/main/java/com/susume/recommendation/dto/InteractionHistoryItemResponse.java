package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public class InteractionHistoryItemResponse {
    @JsonProperty("id")
    public final UUID id;

    @JsonProperty("externalItemId")
    public final String externalItemId;

    @JsonProperty("interactionType")
    public final String interactionType;

    @JsonProperty("timestamp")
    public final Instant timestamp;

    public InteractionHistoryItemResponse(UUID id, String externalItemId,
            String interactionType, Instant timestamp) {
        this.id = id;
        this.externalItemId = externalItemId;
        this.interactionType = interactionType;
        this.timestamp = timestamp;
    }
}
