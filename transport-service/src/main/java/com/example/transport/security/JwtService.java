package com.example.transport.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final Duration ttl;

    public JwtService(
            JwtEncoder encoder,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience,
            @Value("${jwt.ttl}") Duration ttl) {
        if (issuer.isBlank()
                || audience.isBlank()
                || ttl.compareTo(Duration.ofSeconds(1)) < 0
                || ttl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException(
                    "JWT issuer/audience required; TTL must be 1 second to one hour");
        }
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
    }

    public String generateToken(
            String accountId, String username, List<String> roles, long authVersion) {
        return generateTokenUntil(accountId, username, roles, authVersion, Instant.MAX)
                .getTokenValue();
    }

    public Jwt generateTokenUntil(
            String accountId,
            String username,
            List<String> roles,
            long authVersion,
            Instant deadline) {
        if (authVersion < 0) throw new IllegalArgumentException("Invalid authentication version");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiry = now.plus(ttl).isBefore(deadline) ? now.plus(ttl) : deadline;
        if (!expiry.isAfter(now))
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "Expired session");
        var claims =
                JwtClaimsSet.builder()
                        .subject(accountId)
                        .claim("username", username)
                        .issuer(issuer)
                        .audience(List.of(audience))
                        .claim("roles", roles)
                        .claim("auth_version", authVersion)
                        .id(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .notBefore(now)
                        .expiresAt(expiry)
                        .build();
        return encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).build(), claims));
    }

    public long expiresIn() {
        return ttl.toSeconds();
    }
}
