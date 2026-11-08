package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import com.example.transport.TransportApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.*;

class ApplicationSessionRestartIntegrationTest {
    @TempDir Path keys;

    @Test
    void realApplicationRestartPreservesSessionsAndOperatorContextRevokesThem() throws Exception {
        TestPostgres.createDatabase("application_restart");
        var redis = TestRedis.shared();
        String url = TestPostgres.INSTANCE.getJdbcUrl("postgres", "application_restart");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var privateKey = keys.resolve("private.pem");
        var publicKey = keys.resolve("public.pem");
        AuthenticationIntegrationTest.pem(privateKey, "PRIVATE KEY", pair.getPrivate().getEncoded());
        AuthenticationIntegrationTest.pem(publicKey, "PUBLIC KEY", pair.getPublic().getEncoded());
        var properties = new LinkedHashMap<String, String>();
        properties.put("spring.datasource.url", url);
        properties.put("spring.datasource.username", "postgres");
        properties.put("spring.datasource.password", "postgres");
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.flyway.url", url);
        properties.put("spring.flyway.user", "postgres");
        properties.put("spring.flyway.password", "postgres");
        properties.put("spring.jpa.hibernate.ddl-auto", "validate");
        properties.put("spring.data.redis.host", "127.0.0.1");
        properties.put("spring.data.redis.port", "" + redis.port);
        properties.put("spring.data.redis.password", redis.password);
        properties.put("jwt.private-key", privateKey.toUri().toString());
        properties.put("jwt.public-key", publicKey.toUri().toString());
        properties.put("jwt.issuer", "https://auth.test");
        properties.put("jwt.audience", "transport-test");
        properties.put("monitoring.password", "test-monitoring-password");
        properties.put("auth.bootstrap.username", "");
        properties.put("auth.bootstrap.password", "");
        properties.put("server.port", "0");
        String[] args = properties.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()).toArray(String[]::new);
        String token;
        UUID id;
        try (var first = new SpringApplicationBuilder(TransportApplication.class).run(args)) {
            var account = new Account("restart-test", new BCryptPasswordEncoder(4).encode("restart-test-password"), "USER", "restart@test.invalid");
            account.verifyEmail();
            id = first.getBean(AccountRepository.class).saveAndFlush(account).getId();
            token = first.getBean(LoginService.class).login("restart-test", "restart-test-password").getAccessToken();
            assertThat(first.getBean(RedisSessionStore.class).validate(first.getBean(JwtDecoder.class).decode(token))).isPresent();
        }
        try (var second = new SpringApplicationBuilder(TransportApplication.class).run(args)) {
            int port = second.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            var response = RestClient.create("http://127.0.0.1:" + port).get().uri("/api/auth/me")
                    .header("Authorization", "Bearer " + token).retrieve().toEntity(Map.class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("id", id.toString());
        }
        // The exact non-web configuration used by the deployed operator CLI.
        var config = Class.forName("com.example.transport.admin.AccountAdminCli$AdminConfiguration");
        try (var operator = new SpringApplicationBuilder(config).profiles("account-admin")
                .web(org.springframework.boot.WebApplicationType.NONE).run(args)) {
            operator.getBean(AccountSecurityService.class).apply(id, "set-enabled", "false");
            assertThat(operator.getBean(AccountRepository.class).findById(id).orElseThrow().isEnabled()).isFalse();
        }
        // Inspect the session key without repopulating it from PostgreSQL.
        var connection = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory("127.0.0.1", redis.port);
        // Authentication is configured explicitly, as on application startup.
        connection.getStandaloneConfiguration().setPassword(redis.password);
        connection.afterPropertiesSet();
        connection.start();
        try {
            var template = new org.springframework.data.redis.core.StringRedisTemplate(connection);
            assertThat(template.hasKey(RedisSessionStore.keys(id).getFirst())).isFalse();
        } finally { connection.destroy(); }
    }
}
