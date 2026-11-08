package com.example.transport.config;

import com.example.transport.security.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;

import java.time.*;
import java.util.*;

@Configuration
@EnableScheduling
public class BrowserSecurityConfig {
    @Bean
    public CookieCsrfTokenRepository browserCsrfRepository() {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieName("__Host-cdc-csrf");
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookieCustomizer(
                cookie -> cookie.secure(true).httpOnly(true).path("/").sameSite("Lax"));
        return repository;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain browserSecurityChain(
            HttpSecurity http,
            BrowserAuthSettings settings,
            CookieCsrfTokenRepository browserCsrfRepository,
            @Value("${auth.login.max-attempts:10}") int loginAttempts,
            @Value("${auth.login.max-peers:10000}") int maxPeers,
            @Value("${auth.login.window:PT1M}") Duration window,
            @Value("${auth.browser.max-attempts:60}") int refreshAttempts)
            throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(settings.origins());
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setAllowCredentials(true);
        http.securityMatcher(
                        "/api/auth/csrf",
                        "/api/auth/browser-login",
                        "/api/auth/refresh",
                        "/api/auth/browser-logout")
                .cors(c -> c.configurationSource(request -> cors))
                .csrf(
                        c ->
                                c.csrfTokenRepository(browserCsrfRepository)
                                        .csrfTokenRequestHandler(
                                                new XorCsrfTokenRequestAttributeHandler()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/auth/csrf")
                                        .permitAll()
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/auth/browser-login",
                                                "/api/auth/refresh",
                                                "/api/auth/browser-logout")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll());
        http.addFilterBefore(
                new BrowserRequestFilter(settings),
                org.springframework.web.filter.CorsFilter.class);
        http.addFilterBefore(
                new LoginRateLimitFilter(
                        loginAttempts,
                        maxPeers,
                        window,
                        Clock.systemUTC(),
                        Set.of("/api/auth/browser-login")),
                CsrfFilter.class);
        http.addFilterBefore(
                new LoginRateLimitFilter(
                        refreshAttempts,
                        maxPeers,
                        window,
                        Clock.systemUTC(),
                        Set.of("/api/auth/refresh", "/api/auth/browser-logout")),
                CsrfFilter.class);
        return http.build();
    }
}
