package com.example.transport.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

// Only registered in the bearer filter chain, after cryptographic validation and before actor
// context.
public class LiveAccountFilter extends OncePerRequestFilter {
    public static final String ACCOUNT_ATTRIBUTE = "com.example.transport.security.verifiedAccount";
    private final RedisSessionStore accounts;
    private final AuthenticationEntryPoint entryPoint;
    private final ObjectMapper json;

    public LiveAccountFilter(
            RedisSessionStore accounts, AuthenticationEntryPoint entryPoint, ObjectMapper json) {
        this.accounts = accounts;
        this.entryPoint = entryPoint;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            AccountSessionState account;
            try {
                account =
                        accounts.validate(token.getToken())
                                .orElse(null);
            } catch (DataAccessException unavailable) {
                SecurityContextHolder.clearContext();
                response.setStatus(503);
                response.setContentType("application/json");
                json.writeValue(
                        response.getOutputStream(),
                        Map.of(
                                "status",
                                503,
                                "error",
                                "Service Unavailable",
                                "message",
                                "Authentication service unavailable",
                                "timestamp",
                                Instant.now().toString(),
                                "path",
                                request.getRequestURI()));
                return;
            }
            if (account == null) {
                SecurityContextHolder.clearContext();
                entryPoint.commence(
                        request, response, new BadCredentialsException("Invalid authentication"));
                return;
            }
            request.setAttribute(ACCOUNT_ATTRIBUTE, account);
        }
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof JwtAuthenticationToken)
                && !(authentication
                        instanceof
                        org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Bearer authentication required"));
            return;
        }
        chain.doFilter(request, response);
    }
}
