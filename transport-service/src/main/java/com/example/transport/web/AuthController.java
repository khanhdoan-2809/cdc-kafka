package com.example.transport.web;

import com.example.transport.domain.auth.*;
import com.example.transport.security.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final LoginService logins;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return logins.login(request.getUsername(), request.getPassword());
    }
}
