package com.example.transport.web;

import com.example.transport.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessions;

    public record MeResponse(UUID id, String username, String email, String role) {}

    @GetMapping("/me")
    public MeResponse me(
            @RequestAttribute(LiveAccountFilter.ACCOUNT_ATTRIBUTE) AccountSessionState account) {
        return new MeResponse(account.id(), account.username(), account.email(), account.role());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestAttribute(LiveAccountFilter.ACCOUNT_ATTRIBUTE) AccountSessionState account) {
        sessions.logout(account);
    }
}
