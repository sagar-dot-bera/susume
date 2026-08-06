package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.util.UUID;

public record UserRegistrationRequest(
        String email,

        String password,
        String username,

        String firstName,

        String lastName) {

}
