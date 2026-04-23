package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class EmbedResponse {
    @JsonProperty("embedding")
    public List<Float> embedding;

    @JsonProperty("dimension")
    public int dimension;

    public EmbedResponse() {
    }

    public EmbedResponse(List<Float> embedding, int dimension) {
        this.embedding = embedding;
        this.dimension = dimension;
    }
}
