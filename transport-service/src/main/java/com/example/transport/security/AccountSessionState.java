package com.example.transport.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public record AccountSessionState(
        UUID id,
        String username,
        String email,
        String role,
        boolean enabled,
        boolean emailVerified,
        long authVersion) {
    public boolean accepts(Jwt jwt) {
        var version = JwtAuthVersion.read(jwt.getClaim("auth_version"));
        return enabled
                && emailVerified
                && email != null
                && !email.isBlank()
                && Set.of("ADMIN", "USER").contains(role)
                && version.isPresent()
                && version.getAsLong() == authVersion
                && List.of("ROLE_" + role).equals(jwt.getClaim("roles"));
    }
}
