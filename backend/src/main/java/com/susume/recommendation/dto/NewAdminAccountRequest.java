package com.susume.recommendation.dto;

public record NewAdminAccountRequest(
        UserRegistrationRequest userRegistrationRequest,
        TenantRegistrationRequest tenantRegistrationRequest) {

}
