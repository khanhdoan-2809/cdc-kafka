package com.example.transport.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Operator-only boundary. All future password/security changes must use this lock/revoke order. */
@Service
@RequiredArgsConstructor
public class AccountSecurityService {
    private final AccountRepository accounts;
    private final RedisSessionStore sessions;

    @Transactional
    public void apply(UUID accountId, String operation, String value) {
        // Validate before revoking anything, including unknown CLI operations.
        if (!java.util.Set.of("revoke", "set-role", "set-enabled", "delete").contains(operation))
            throw new IllegalArgumentException("Unsupported account operation");
        if (operation.equals("set-role") && !java.util.Set.of("USER", "ADMIN").contains(value))
            throw new IllegalArgumentException("Role must be USER or ADMIN");
        if (operation.equals("set-enabled") && !java.util.Set.of("true", "false").contains(value))
            throw new IllegalArgumentException("Enabled must be true or false");
        var account = accounts.lockById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        sessions.revokeAll(accountId);
        switch (operation) {
            case "set-role" -> account.changeRole(value);
            case "set-enabled" -> account.setEnabled(Boolean.parseBoolean(value));
            case "delete" -> accounts.delete(account);
            default -> { }
        }
        // A failed DB commit may sign the user out, but cannot restore revoked sessions.
    }
}
