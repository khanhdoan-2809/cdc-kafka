package com.example.transport.config;

import com.example.transport.security.LoginRateLimitFilter;
import com.example.transport.web.context.AuthenticatedActorFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Thay thế cho @EnableGlobalMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${auth.login.max-attempts:10}") int maxAttempts,
            @Value("${auth.login.max-peers:10000}") int maxPeers,
            @Value("${auth.login.window:PT1M}") Duration window)
            throws Exception {
        http
                // 1. Tắt CSRF vì API stateless
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Cấu hình CORS (nếu cần)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. Stateless session (không tạo session)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. Exception handling cho 401/403
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))

                // 5. Phân quyền endpoint
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(HttpMethod.POST, "/api/auth/login")
                                        .permitAll()
                                        .requestMatchers("/api/public/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/actuator/health",
                                                "/actuator/health/liveness",
                                                "/actuator/health/readiness")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())

                // 6. Kích hoạt OAuth2 Resource Server với JWT
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler)
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter())));

        http.addFilterBefore(
                        new LoginRateLimitFilter(maxAttempts, maxPeers, window, Clock.systemUTC()),
                        BearerTokenAuthenticationFilter.class)
                .addFilterAfter(
                        new AuthenticatedActorFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        // Nếu JWT chứa roles trong claim "roles"
        // và giá trị là ["ADMIN", "USER"] (không có prefix ROLE_)
        // thì cần thêm prefix ROLE_. Hiện tại token chứa ["ROLE_ADMIN", "ROLE_USER"],
        // nên giữ nguyên authority bằng prefix rỗng để hasRole('ADMIN') hoạt động.
        grantedAuthoritiesConverter.setAuthorityPrefix("");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

        var jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        // (Tùy chọn) Đặt claim làm principal name (mặc định là "sub")
        // sub hiện là UUID tài khoản đã xác thực, được dùng làm actor trong audit.
        jwtAuthenticationConverter.setPrincipalClaimName("sub");

        return jwtAuthenticationConverter;
    }
}
