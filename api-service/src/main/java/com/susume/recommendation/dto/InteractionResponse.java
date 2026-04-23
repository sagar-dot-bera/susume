package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public class InteractionResponse {
    @JsonProperty("id")
    public final UUID id;

    @JsonProperty("externalUserId")
    public final String externalUserId;

    @JsonProperty("externalItemId")
    public final String externalItemId;

    @JsonProperty("interactionType")
    public final String interactionType;

    @JsonProperty("timestamp")
    public final Instant timestamp;

    public InteractionResponse(UUID id, String externalUserId, String externalItemId,
            String interactionType, Instant timestamp) {
        this.id = id;
        this.externalUserId = externalUserId;
        this.externalItemId = externalItemId;
        this.interactionType = interactionType;
        this.timestamp = timestamp;
    }
}
