package com.example.transport.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AccountBootstrap implements ApplicationRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AccountBootstrap(
            AccountRepository accounts,
            PasswordEncoder encoder,
            @Value("${auth.bootstrap.username:}") String username,
            @Value("${auth.bootstrap.password:}") String password) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isEmpty() && password.isEmpty()) return;
        if (!username.matches("[A-Za-z0-9._@-]{1,100}")
                || password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Invalid bootstrap credentials: username 1-100 ASCII characters, password 12"
                            + " characters to 72 UTF-8 bytes required");
        }
        // Bootstrap only an empty account store. Never reset an existing password.
        if (accounts.count() == 0)
            accounts.saveAndFlush(new Account(username, encoder.encode(password), "ADMIN"));
    }
}
