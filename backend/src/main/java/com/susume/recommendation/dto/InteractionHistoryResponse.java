package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class InteractionHistoryResponse {
    @JsonProperty("data")
    public final List<InteractionHistoryItemResponse> data;

    @JsonProperty("limit")
    public final int limit;

    public InteractionHistoryResponse(List<InteractionHistoryItemResponse> data,
            int limit) {
        this.data = data;
        this.limit = limit;
    }
}
