package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TenantRegistrationRequest {
    @JsonProperty("name")
    private String name;

    @JsonProperty("slug")
    private String slug;

}
