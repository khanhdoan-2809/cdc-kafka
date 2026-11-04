package com.example.transport.domain.auth;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    @Size(max = 100)
    private String username;

    @NotBlank
    @Size(max = 256)
    private String password;
}
