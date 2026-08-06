package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class RecordInteractionRequest {
    @JsonProperty("externalUserId")
    public String externalUserId;

    @JsonProperty("externalItemId")
    public String externalItemId;

    @JsonProperty("interactionType")
    public String interactionType;

    @JsonProperty("timestamp")
    public Instant timestamp;

    public RecordInteractionRequest() {
    }

    public RecordInteractionRequest(String externalUserId, String externalItemId,
            String interactionType, Instant timestamp) {
        this.externalUserId = externalUserId;
        this.externalItemId = externalItemId;
        this.interactionType = interactionType;
        this.timestamp = timestamp;
    }
}
