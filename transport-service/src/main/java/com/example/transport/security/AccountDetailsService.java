package com.example.transport.security;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountDetailsService implements UserDetailsService {
    private final AccountRepository accounts;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return new AccountPrincipal(
                accounts.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials")));
    }
}
