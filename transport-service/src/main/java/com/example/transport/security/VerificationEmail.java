package com.example.transport.security;

public record VerificationEmail(String recipient, String token) {
    @Override
    public String toString() {
        return "VerificationEmail[redacted]";
    }
}
