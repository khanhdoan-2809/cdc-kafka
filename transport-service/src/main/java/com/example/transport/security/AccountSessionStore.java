package com.example.transport.security;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@RequiredArgsConstructor
public class AccountSessionStore {
    private final JdbcTemplate jdbc;

    public Optional<AccountSessionState> findById(UUID id) {
        // One fresh primary-key query per bearer request. Never load passwords or cache this
        // result.
        return jdbc
                .query(
                        "select id,username,email,role,enabled,email_verified,auth_version from"
                            + " auth_user where id=?",
                        (rs, row) ->
                                new AccountSessionState(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("username"),
                                        rs.getString("email"),
                                        rs.getString("role"),
                                        rs.getBoolean("enabled"),
                                        rs.getBoolean("email_verified"),
                                        rs.getLong("auth_version")),
                        id)
                .stream()
                .findFirst();
    }
}
