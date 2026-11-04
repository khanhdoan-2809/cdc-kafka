package com.example.transport.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.*;
import java.util.*;

// Per-process, per-peer-IP limit. Do not trust caller-supplied forwarded headers.
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private record Window(Instant start, int attempts) {}

    private final Map<String, Window> windows = new HashMap<>();
    private final int attempts;
    private final int maxPeers;
    private final Duration duration;
    private final Clock clock;

    public LoginRateLimitFilter(int attempts, int maxPeers, Duration duration, Clock clock) {
        if (attempts < 1 || maxPeers < 1 || duration.isNegative() || duration.isZero())
            throw new IllegalArgumentException("Invalid login rate limit");
        this.attempts = attempts;
        this.maxPeers = maxPeers;
        this.duration = duration;
        this.clock = clock;
    }

    synchronized boolean allow(String peer) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> !entry.getValue().start().plus(duration).isAfter(now));
        Window window = windows.get(peer);
        if (window == null) {
            if (windows.size() >= maxPeers) return false;
            windows.put(peer, new Window(now, 1));
            return true;
        }
        if (window.attempts() >= attempts) return false;
        windows.put(peer, new Window(window.start(), window.attempts() + 1));
        return true;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && "/api/auth/login"
                        .equals(
                                request.getRequestURI()
                                        .substring(request.getContextPath().length())));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", Long.toString(Math.max(1, duration.toSeconds())));
            response.getWriter()
                    .write(
                            "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Try"
                                    + " again later\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
