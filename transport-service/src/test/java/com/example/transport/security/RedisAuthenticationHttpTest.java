package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** HTTP/security/transaction boundary tests with a mocked store; Lua has separate real Redis tests. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:redis_http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop", "jwt.issuer=https://auth.test",
        "jwt.audience=transport-test", "jwt.ttl=PT15M", "monitoring.password=test-monitoring-password",
        "auth.bootstrap.username=", "auth.bootstrap.password=", "auth.login.max-attempts=1000"})
@AutoConfigureMockMvc
class RedisAuthenticationHttpTest {
    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry properties) throws Exception {
        var directory = Files.createTempDirectory("redis-http-keys-");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var privateKey = directory.resolve("private.pem");
        var publicKey = directory.resolve("public.pem");
        AuthenticationIntegrationTest.pem(privateKey, "PRIVATE KEY", pair.getPrivate().getEncoded());
        AuthenticationIntegrationTest.pem(publicKey, "PUBLIC KEY", pair.getPublic().getEncoded());
        properties.add("jwt.private-key", () -> privateKey.toUri().toString());
        properties.add("jwt.public-key", () -> publicKey.toUri().toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired JwtDecoder decoder;
    @MockitoBean RedisSessionStore sessions;
    AtomicReference<AccountSessionState> created;

    @BeforeEach
    void account() {
        accounts.deleteAll();
        var account = new Account("http-user", new BCryptPasswordEncoder(4).encode("http-test-password"), "USER", "http@test.invalid");
        account.verifyEmail();
        accounts.saveAndFlush(account);
        created = new AtomicReference<>();
        doAnswer(call -> { created.set(call.getArgument(0)); return null; })
                .when(sessions).create(any(AccountSessionState.class));
    }

    String login() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"http-user\",\"password\":\"http-test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("tokenType").value("Bearer"))
                .andExpect(jsonPath("expiresIn").value(900)).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void loginMeAndLogoutRetainTheHttpContract() throws Exception {
        String token = login();
        assertThat(created.get().accepts(decoder.decode(token))).isTrue();
        when(sessions.validate(any())).thenReturn(Optional.of(created.get()));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("username").value("http-user"))
                .andExpect(jsonPath("email").value("http@test.invalid"))
                .andExpect(jsonPath("role").value("USER"))
                .andExpect(jsonPath("sessionId").doesNotExist()).andExpect(jsonPath("passwordHash").doesNotExist());
        when(sessions.revokeAllIfActive(created.get())).thenReturn(true);
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        verify(sessions).revokeAllIfActive(created.get());
        when(sessions.validate(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storeFailuresMapTo503AtLoginValidationAndLogout() throws Exception {
        String token = login();
        when(sessions.validate(any())).thenReturn(Optional.of(created.get()));
        when(sessions.revokeAllIfActive(any())).thenThrow(new DataAccessResourceFailureException("private detail"));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("message").value("Authentication service unavailable"));
        when(sessions.validate(any())).thenThrow(new DataAccessResourceFailureException("private detail"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
        doThrow(new DataAccessResourceFailureException("private detail"))
                .when(sessions).create(any(AccountSessionState.class));
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"http-user\",\"password\":\"http-test-password\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("accessToken").doesNotExist());
    }
}
