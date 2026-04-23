package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ItemListResponse {
    @JsonProperty("data")
    public final List<ItemDetailResponse> data;

    @JsonProperty("nextCursor")
    public final String nextCursor;

    @JsonProperty("limit")
    public final int limit;

    public ItemListResponse(List<ItemDetailResponse> data, String nextCursor, int limit) {
        this.data = data;
        this.nextCursor = nextCursor;
        this.limit = limit;
    }
}
