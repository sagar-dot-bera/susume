package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TenantRegistrationRequest {
    @JsonProperty("name")
    public String name;

    @JsonProperty("contactEmail")
    public String contactEmail;

    public TenantRegistrationRequest() {
    }

    public TenantRegistrationRequest(String name, String contactEmail) {
        this.name = name;
        this.contactEmail = contactEmail;
    }
}
