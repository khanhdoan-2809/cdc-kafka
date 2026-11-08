package com.example.transport.security;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "auth_user")
public class Account {
    @Id private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(unique = true, length = 254)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    protected Account() {}

    public Account(String username, String passwordHash, String role) {
        if (!java.util.Set.of("ADMIN", "USER").contains(role))
            throw new IllegalArgumentException("Unsupported role");
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
    }

    public Account(String username, String passwordHash, String role, String email) {
        this(username, passwordHash, role);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void verifyEmail() {
        if (email == null || email.isBlank()) throw new IllegalStateException("Email is required");
        if (!emailVerified) {
            invalidateSessions();
            this.emailVerified = true;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            invalidateSessions();
            this.enabled = enabled;
        }
    }

    public long getAuthVersion() {
        return authVersion;
    }

    public void invalidateSessions() {
        authVersion = Math.incrementExact(authVersion);
    }
}
