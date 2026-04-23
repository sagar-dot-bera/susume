package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class InteractionHistoryResponse {
    @JsonProperty("data")
    public final List<InteractionHistoryItemResponse> data;

    @JsonProperty("nextCursor")
    public final String nextCursor;

    @JsonProperty("limit")
    public final int limit;

    public InteractionHistoryResponse(List<InteractionHistoryItemResponse> data,
            String nextCursor, int limit) {
        this.data = data;
        this.nextCursor = nextCursor;
        this.limit = limit;
    }
}
