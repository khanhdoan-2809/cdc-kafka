package com.example.transport.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;

@Component
public class BrowserAuthSettings {
    private final boolean enabled;
    private final List<String> origins;

    public BrowserAuthSettings(
            @Value("${auth.browser.enabled:false}") boolean enabled,
            @Value("${auth.browser.allowed-origins:}") String origins,
            JwtService jwt) {
        this.enabled = enabled;
        this.origins =
                Arrays.stream(origins.split(","))
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList();
        if (enabled && (this.origins.isEmpty() || jwt.expiresIn() != 900))
            throw new IllegalArgumentException(
                    "Browser auth requires HTTPS origins and 15-minute JWTs");
        for (String origin : this.origins) {
            URI uri = URI.create(origin);
            if (!"https".equals(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || !uri.getRawPath().isEmpty()
                    || uri.getPort() == 0
                    || uri.getPort() > 65535
                    || !origin.equals("https://" + uri.getRawAuthority()))
                throw new IllegalArgumentException(
                        "Browser origins must be explicit HTTPS origins without paths");
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public List<String> origins() {
        return origins;
    }
}
