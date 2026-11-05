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
        this.enabled = enabled;
    }
}
