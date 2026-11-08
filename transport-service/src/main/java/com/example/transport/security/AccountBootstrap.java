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
    private final String email;
    private final RegistrationService verification;

    public AccountBootstrap(
            AccountRepository accounts,
            PasswordEncoder encoder,
            @Value("${auth.bootstrap.username:}") String username,
            @Value("${auth.bootstrap.password:}") String password,
            @Value("${auth.bootstrap.email:}") String email,
            RegistrationService verification) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
        this.email = email.strip().toLowerCase(java.util.Locale.ROOT);
        this.verification = verification;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isEmpty() && password.isEmpty()) return;
        // Bootstrap only an empty account store. Never reset an existing password.
        if (accounts.count() != 0) return;
        try {
            var address = new jakarta.mail.internet.InternetAddress(email, true);
            address.validate();
            if (!email.equals(address.getAddress()) || email.length() > 254 || !email.contains("@"))
                throw new IllegalArgumentException("Invalid bootstrap email");
        } catch (jakarta.mail.internet.AddressException invalid) {
            throw new IllegalStateException("A valid bootstrap email is required", invalid);
        }
        if (!username.matches("[A-Za-z0-9._@-]{1,100}")
                || password.codePointCount(0, password.length()) < 15
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Invalid bootstrap credentials: username 1-100 ASCII characters, password 15"
                            + " characters to 72 UTF-8 bytes required");
        }
        accounts.saveAndFlush(new Account(username, encoder.encode(password), "ADMIN", email));
        verification.resend(email);
    }
}
