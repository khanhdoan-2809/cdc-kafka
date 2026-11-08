package com.example.transport.security;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final AccountRepository accounts;

    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('ADMIN','USER') and #authenticated.id().toString() == authentication.name")
    @Transactional
    public void logout(AccountSessionState authenticated) {
        var account =
                accounts.lockById(authenticated.id())
                        .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
        // Recheck after taking the lock: another logout/status update may have won the race.
        if (!account.isEnabled()
                || !account.isEmailVerified()
                || account.getAuthVersion() != authenticated.authVersion()
                || !account.getRole().equals(authenticated.role()))
            throw new BadCredentialsException("Invalid authentication");
        account.invalidateSessions();
    }
}
