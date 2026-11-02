package com.example.transport.domain.auth;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}