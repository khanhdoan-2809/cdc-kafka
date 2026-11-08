package com.example.transport.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Only attached to the browser chain; never trusts caller-supplied forwarded headers. */
public class BrowserRequestFilter extends OncePerRequestFilter {
    private final BrowserAuthSettings settings;

    public BrowserRequestFilter(BrowserAuthSettings settings) {
        this.settings = settings;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!settings.enabled()) {
            response.setStatus(404);
            return;
        }
        boolean unsafe = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (!request.isSecure()
                || (unsafe
                        && (java.util.Collections.list(request.getHeaders("Origin")).size() != 1
                                || !settings.origins().contains(request.getHeader("Origin"))))) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
