package com.example.transport.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
public class MonitoringSecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain monitoringSecurity(
            HttpSecurity http,
            PasswordEncoder encoder,
            AccessDeniedHandler deniedHandler,
            @Value("${monitoring.password}") String password)
            throws Exception {
        if (password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException(
                    "Monitoring password must be 12 characters to 72 UTF-8 bytes");
        var users =
                new InMemoryUserDetailsManager(
                        User.withUsername("monitoring")
                                .password(encoder.encode(password))
                                .roles("MONITORING")
                                .build());
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        AuthenticationEntryPoint basicEntryPoint =
                (request, response, exception) -> {
                    response.setStatus(401);
                    response.setHeader("WWW-Authenticate", "Basic realm=\"monitoring\"");
                    response.setContentType("application/json");
                    response.getWriter()
                            .write(
                                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication"
                                        + " required\"}");
                };
        http.securityMatcher(
                        "/actuator/prometheus",
                        "/actuator/metrics",
                        "/actuator/metrics/**",
                        "/actuator/info")
                .authenticationManager(new ProviderManager(provider))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("MONITORING"))
                .httpBasic(basic -> basic.authenticationEntryPoint(basicEntryPoint))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(basicEntryPoint)
                                        .accessDeniedHandler(deniedHandler));
        return http.build();
    }
}
