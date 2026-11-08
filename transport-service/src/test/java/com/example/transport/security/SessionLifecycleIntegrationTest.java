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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "jwt.issuer=https://auth.test",
            "jwt.audience=transport-test",
            "jwt.ttl=PT15M",
            "monitoring.password=test-monitoring-password",
            "auth.bootstrap.username=",
            "auth.bootstrap.password=",
            "auth.login.max-attempts=1000"
        })
@AutoConfigureMockMvc
class SessionLifecycleIntegrationTest {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        TestPostgres.createDatabase("sessions_test");
        String url = TestPostgres.INSTANCE.getJdbcUrl("postgres", "sessions_test");
        for (String prefix : List.of("spring.datasource.", "spring.flyway.")) {
            registry.add(prefix + "url", () -> url);
            registry.add(
                    prefix + (prefix.contains("flyway") ? "user" : "username"), () -> "postgres");
            registry.add(prefix + "password", () -> "postgres");
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        AuthenticationIntegrationTest.keys(registry);
    }

    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcTemplate jdbc;
    @MockitoSpyBean AccountRepository accounts;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired AccountSecurityService security;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean org.springframework.security.authentication.AuthenticationManager authenticationManager;
    @MockitoSpyBean RedisSessionStore store;
    @MockitoSpyBean PlatformTransactionManager transactions;
    Account alice, bob, admin;
    static final String PASSWORD = "session-test-password";

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE transport, auth_user RESTART IDENTITY CASCADE");
        var hash = new BCryptPasswordEncoder(4).encode(PASSWORD);
        alice = fixture("alice", "USER", hash);
        bob = fixture("bob", "USER", hash);
        admin = fixture("admin", "ADMIN", hash);
    }

    Account fixture(String name, String role, String hash) {
        var account = new Account(name, hash, role, name + "@test.invalid");
        account.verifyEmail();
        return accounts.saveAndFlush(account);
    }

    String login(Account account) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", account.getUsername(), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("expiresIn").value(900)).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token));
    }

    ResultActions logout(String token) throws Exception {
        return mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token));
    }

    @Test
    void meExposesOnlyPublicFieldsAndPerformsNoAccountSql() throws Exception {
        String token = login(alice);
        var jwt = decoder.decode(token);
        assertThat(UUID.fromString(jwt.getId())).isNotNull();
        assertThat(jwt.getClaims()).doesNotContainKey("auth_version");
        clearInvocations(accounts, jdbc, store);
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        var result = me(token).andExpect(status().isOk())
                .andExpect(jsonPath("id").value(alice.getId().toString()))
                .andExpect(jsonPath("username").value("alice"))
                .andExpect(jsonPath("email").value("alice@test.invalid"))
                .andExpect(jsonPath("role").value("USER")).andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsString()).size()).isEqualTo(4);
        verifyNoInteractions(accounts, jdbc);
        verify(store, times(1)).validate(any(Jwt.class));
        assertThat(statistics.getPrepareStatementCount()).isZero();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesEveryOwnSessionAndCannotTargetAnotherAccount() throws Exception {
        String one = login(alice), two = login(alice), other = login(bob);
        assertThat(one).isNotEqualTo(two);
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + one)
                .header("X-User-Id", bob.getId()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("id", bob.getId()))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        me(one).andExpect(status().isUnauthorized());
        me(two).andExpect(status().isUnauthorized());
        logout(two).andExpect(status().isUnauthorized());
        me(other).andExpect(status().isOk());
        me(login(alice)).andExpect(status().isOk());
    }

    @Test
    void controlledDisableRoleChangeAndDeletionRevokeSessions() throws Exception {
        String disabled = login(alice), changed = login(bob), deleted = login(admin);
        security.apply(alice.getId(), "set-enabled", "false");
        security.apply(bob.getId(), "set-role", "ADMIN");
        security.apply(admin.getId(), "delete", "");
        for (String token : List.of(disabled, changed, deleted)) {
            me(token).andExpect(status().isUnauthorized());
            logout(token).andExpect(status().isUnauthorized());
        }
        security.apply(alice.getId(), "set-enabled", "true");
        me(disabled).andExpect(status().isUnauthorized());
        me(login(alice)).andExpect(status().isOk());
        me(login(bob)).andExpect(status().isOk()).andExpect(jsonPath("role").value("ADMIN"));
    }

    @Test
    void rawSqlDoesNotUpdateSessionSnapshotButControlledRevocationDoes() throws Exception {
        String token = login(alice);
        jdbc.update("update auth_user set role='ADMIN' where id=?", alice.getId());
        me(token).andExpect(status().isOk()).andExpect(jsonPath("role").value("USER"));
        security.apply(alice.getId(), "revoke", "");
        me(token).andExpect(status().isUnauthorized());
    }

    String signed(String jti, List<String> roles) {
        var claims = JwtClaimsSet.builder().subject(alice.getId().toString())
                .issuer("https://auth.test").audience(List.of("transport-test"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("username", "alice").claim("roles", roles);
        if (jti != null) claims.id(jti);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();
    }

    @Test
    void missingMalformedAndLegacySessionClaimsFailBeforeRedis() throws Exception {
        clearInvocations(store);
        me(signed(null, List.of("ROLE_USER"))).andExpect(status().isUnauthorized());
        for (String id : List.of("bad", "1-1-1-1-1", ""))
            me(signed(id, List.of("ROLE_USER"))).andExpect(status().isUnauthorized());
        me("invalid").andExpect(status().isUnauthorized());
        verifyNoInteractions(store);
        me(signed(UUID.randomUUID().toString(), List.of("ROLE_USER")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void redisFailureReturns503WithoutBusinessWritesOrFallback() throws Exception {
        String token = login(admin);
        doThrow(new DataAccessResourceFailureException("private Redis connection detail"))
                .when(store).validate(any(Jwt.class));
        me(token).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("message").value("Authentication service unavailable"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(post("/api/transports").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"reference\":\"DENIED\"}"))
                .andExpect(status().isServiceUnavailable());
        logout(token).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from transport", Long.class)).isZero();
    }

    @Test
    void redisFailureAbortsLoginAndAccountMutation() throws Exception {
        doThrow(new DataAccessResourceFailureException("private"))
                .when(store).create(any(AccountSessionState.class));
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", "alice", "password", PASSWORD))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("accessToken").doesNotExist());
        doThrow(new DataAccessResourceFailureException("private"))
                .when(store).revokeAll(alice.getId());
        assertThatThrownBy(() -> security.apply(alice.getId(), "set-enabled", "false"))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(accounts.findById(alice.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void databaseFailureAtLogoutReturns503AndDoesNotClaimSuccess() throws Exception {
        String token = login(alice);
        doThrow(new CannotCreateTransactionException("private database details"))
                .when(transactions).getTransaction(any(TransactionDefinition.class));
        me(token).andExpect(status().isOk());
        logout(token).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("message").value("Authentication service unavailable"));
    }

    @Test
    void failedDatabaseMutationAfterRevocationCannotRestoreSessions() throws Exception {
        String token = login(alice);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated delete failure"))
                .when(accounts).delete(any(Account.class));
        assertThatThrownBy(() -> security.apply(alice.getId(), "delete", ""))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(accounts.existsById(alice.getId())).isTrue();
        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWaitsForCredentialValidationThenRevokesTheNewToken() throws Exception {
        String existing = login(alice);
        var captured = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var logoutAttempted = new CountDownLatch(1);
        doAnswer(invocation -> {
            var authenticated = invocation.callRealMethod();
            captured.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Login latch timed out");
            return authenticated;
        }).when(authenticationManager).authenticate(any(org.springframework.security.core.Authentication.class));
        doAnswer(invocation -> {
            logoutAttempted.countDown();
            return invocation.callRealMethod();
        }).when(accounts).lockById(alice.getId());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var pendingLogin = pool.submit(() -> login(alice));
            try {
                assertThat(captured.await(15, TimeUnit.SECONDS)).isTrue();
                var pendingLogout = pool.submit(() -> logout(existing).andReturn().getResponse().getStatus());
                assertThat(logoutAttempted.await(15, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> pendingLogout.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(pendingLogout.get(15, TimeUnit.SECONDS)).isEqualTo(204);
                me(pendingLogin.get(15, TimeUnit.SECONDS)).andExpect(status().isUnauthorized());
            } finally { release.countDown(); }
        }
    }

    @Test
    void simultaneousLogoutsHaveOneWinner() throws Exception {
        String one = login(alice), two = login(alice);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return logout(one).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { start.await(); return logout(two).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 401);
        }
        me(one).andExpect(status().isUnauthorized());
        me(two).andExpect(status().isUnauthorized());
    }

    @Test
    void unexpectedNonBearerIdentityCannotBypassValidation() throws Exception {
        var identity = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(alice.getId().toString()).roles("USER");
        mvc.perform(get("/api/auth/me").with(identity)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs/1").with(identity)).andExpect(status().isUnauthorized());
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_AUTH_BENCHMARK", matches = "true")
    void compareLegacyDatabaseLookupWithRedisSessionLookup() throws Exception {
        var token = decoder.decode(login(alice));
        String sql = "select id,username,email,role,enabled,email_verified,auth_version from auth_user where id=?";
        for (int i = 0; i < 30; i++) {
            jdbc.queryForMap(sql, alice.getId());
            store.validate(token);
        }
        long[] database = new long[300], redis = new long[300];
        for (int i = 0; i < database.length; i++) {
            long started = System.nanoTime();
            jdbc.queryForMap(sql, alice.getId());
            database[i] = System.nanoTime() - started;
            started = System.nanoTime();
            assertThat(store.validate(token)).isPresent();
            redis[i] = System.nanoTime() - started;
        }
        Arrays.sort(database);
        Arrays.sort(redis);
        var report = Map.of("samples", database.length,
                "scope", "local warmed account/session lookup, excluding signature and HTTP",
                "postgresP50Micros", database[150] / 1000.0, "postgresP95Micros", database[285] / 1000.0,
                "redisP50Micros", redis[150] / 1000.0, "redisP95Micros", redis[285] / 1000.0,
                "accountSqlQueriesPerValidationBefore", 1, "accountSqlQueriesPerValidationAfter", 0);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/auth-session-benchmark.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

}
