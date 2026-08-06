package com.susume.recommendation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String username,
        @Email String email,
        @Size(min = 8) String password) {

    public RegisterRequest(String firstName2, String lastName2, String username2, String email2) {
        this(firstName2, lastName2, username2, email2, null);
    }

}
