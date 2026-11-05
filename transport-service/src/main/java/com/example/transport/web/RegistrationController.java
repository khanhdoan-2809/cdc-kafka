package com.example.transport.web;

import com.example.transport.security.RegistrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RegistrationController {
    private final RegistrationService registration;

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._@-]{1,100}") String username,
            @NotBlank @Email @Pattern(regexp = "[ -~]+") @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
        public RegisterRequest {
            email = normalize(email);
        }

        @Override
        public String toString() {
            return "RegisterRequest[redacted]";
        }
    }

    public record EmailRequest(
            @NotBlank @Email @Pattern(regexp = "[ -~]+") @Size(max = 254) String email) {
        public EmailRequest {
            email = normalize(email);
        }
    }

    public record VerifyRequest(@NotBlank @Size(max = 128) String token) {
        @Override
        public String toString() {
            return "VerifyRequest[redacted]";
        }
    }

    private static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    private static final Map<String, String> ACCEPTED =
            Map.of("message", "If eligible, verification instructions will be sent.");

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        registration.register(request.username(), request.email(), request.password());
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resend(@Valid @RequestBody EmailRequest request) {
        registration.resend(request.email());
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verify(@Valid @RequestBody VerifyRequest request) {
        registration.verify(request.token());
        return ResponseEntity.ok(Map.of("message", "Email verified. You can now log in."));
    }
}
