package com.example.transport.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RecordAccess {
    public boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    public UUID userId() {
        // Called only from role-protected service methods; JWT sub is a validated account UUID.
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
