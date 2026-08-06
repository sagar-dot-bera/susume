package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class CreateItemRequest {
    @JsonProperty("externalItemId")
    public String externalItemId;

    @JsonProperty("metadata")
    public Map<String, Object> metadata;

    public CreateItemRequest() {
    }

    public CreateItemRequest(String externalItemId, Map<String, Object> metadata) {
        this.externalItemId = externalItemId;
        this.metadata = metadata;
    }
}
