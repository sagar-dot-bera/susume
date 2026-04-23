package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class UpdateItemRequest {
    @JsonProperty("metadata")
    public Map<String, Object> metadata;

    public UpdateItemRequest() {
    }

    public UpdateItemRequest(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
