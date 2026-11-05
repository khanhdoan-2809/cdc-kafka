package com.example.transport.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    private final String accountId;

    public AccountPrincipal(Account account) {
        super(
                account.getUsername(),
                account.getPasswordHash(),
                account.isEnabled() && account.isEmailVerified(),
                true,
                true,
                true,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole())));
        this.accountId = account.getId().toString();
    }

    public String getAccountId() {
        return accountId;
    }
}
