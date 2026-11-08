package com.example.transport.security;

import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.*;

/** Public account snapshot bound to one active Redis session. */
public record AccountSessionState(
        UUID id, String username, String email, String role, UUID sessionId, Instant expiresAt) {
    public boolean accepts(Jwt jwt) {
        return id != null && sessionId != null && expiresAt != null
                && username != null && email != null && !email.isBlank()
                && role != null && Set.of("ADMIN", "USER").contains(role)
                && id.toString().equals(jwt.getSubject())
                && sessionId.toString().equals(jwt.getId())
                && username.equals(jwt.getClaimAsString("username"))
                && expiresAt.equals(jwt.getExpiresAt())
                && List.of("ROLE_" + role).equals(jwt.getClaim("roles"));
    }
}
