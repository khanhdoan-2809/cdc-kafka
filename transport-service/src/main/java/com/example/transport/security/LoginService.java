package com.example.transport.security;

import com.example.transport.domain.auth.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginService {
    private final AccountRepository accounts;
    private final AuthenticationManager authenticationManager;
    private final JwtService tokens;
    private final RedisSessionStore sessions;

    @Transactional
    public LoginResponse login(String username, String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new BadCredentialsException("Invalid credentials");
        // Lock BEFORE credential validation, through Redis creation. Logout and security
        // changes cannot interleave between credential validation and session creation.
        var account = accounts.lockByUsername(username).orElse(null);
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        var principal = (AccountPrincipal) authentication.getPrincipal();
        if (account == null || !account.getId().toString().equals(principal.getAccountId())
                || account.getEmail() == null || account.getEmail().isBlank())
            throw new BadCredentialsException("Invalid credentials");
        var jwt = tokens.generateToken(principal.getAccountId(), principal.getUsername(),
                List.of("ROLE_" + account.getRole()));
        sessions.create(new AccountSessionState(account.getId(), account.getUsername(),
                account.getEmail(), account.getRole(), java.util.UUID.fromString(jwt.getId()),
                jwt.getExpiresAt()));
        return new LoginResponse(jwt.getTokenValue(), "Bearer", tokens.expiresIn());
    }
}
