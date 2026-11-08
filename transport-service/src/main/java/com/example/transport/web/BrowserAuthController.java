package com.example.transport.web;

import com.example.transport.domain.auth.*;
import com.example.transport.security.*;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class BrowserAuthController {
    public static final String REFRESH_COOKIE = "__Host-cdc-refresh";
    private final AuthenticationManager authenticationManager;
    private final BrowserSessionService sessions;
    private final CookieCsrfTokenRepository browserCsrfRepository;
    private final Clock clock;

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/browser-login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (body.getPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BadCredentialsException("Invalid credentials");
        var authenticated =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                body.getUsername(), body.getPassword()));
        var credentials = sessions.login((AccountPrincipal) authenticated.getPrincipal());
        setRefresh(response, credentials);
        browserCsrfRepository.saveToken(
                browserCsrfRepository.generateToken(request), request, response);
        return credentials.access();
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            var credentials = sessions.refresh(refreshCookie(request));
            setRefresh(response, credentials);
            return credentials.access();
        } catch (BadCredentialsException rejected) {
            clearCookies(request, response);
            throw rejected;
        }
    }

    @PostMapping("/browser-logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            sessions.logout(refreshCookie(request));
        } catch (BadCredentialsException rejected) {
            clearCookies(request, response);
            throw rejected;
        }
        clearCookies(request, response);
    }

    private String refreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        var found =
                Arrays.stream(request.getCookies())
                        .filter(c -> REFRESH_COOKIE.equals(c.getName()))
                        .toList();
        return found.size() == 1 ? found.getFirst().getValue() : null;
    }

    private void setRefresh(
            HttpServletResponse response, BrowserSessionService.Credentials credentials) {
        Duration remaining = Duration.between(clock.instant(), credentials.expiresAt());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie(
                        credentials.refreshToken(),
                        remaining.isNegative() ? Duration.ZERO : remaining));
    }

    private String cookie(String value, Duration age) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .secure(true)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(age)
                .build()
                .toString();
    }

    private void clearCookies(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO));
        browserCsrfRepository.saveToken(null, request, response);
    }
}
