package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class TenantInfoResponse {
    @JsonProperty("id")
    public UUID id;

    @JsonProperty("name")
    public String name;

    @JsonProperty("contactEmail")
    public String contactEmail;

    @JsonProperty("createdAt")
    public Instant createdAt;

    @JsonProperty("status")
    public String status;

    @JsonProperty("slug")
    public String slug;

}
