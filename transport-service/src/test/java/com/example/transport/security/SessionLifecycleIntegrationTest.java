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
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;

    @MockitoSpyBean
    org.springframework.security.authentication.AuthenticationManager authenticationManager;

    @MockitoSpyBean AccountSessionStore store;
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
        var result =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "username",
                                                                account.getUsername(),
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("expiresIn").value(900))
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token));
    }

    ResultActions logout(String token) throws Exception {
        return mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token));
    }

    @Test
    void meExposesOnlyCurrentPublicAccountFieldsAndRequiresAuthentication() throws Exception {
        String token = login(alice);
        var result =
                me(token)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("id").value(alice.getId().toString()))
                        .andExpect(jsonPath("username").value("alice"))
                        .andExpect(jsonPath("email").value("alice@test.invalid"))
                        .andExpect(jsonPath("role").value("USER"))
                        .andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsString()).size()).isEqualTo(4);
        assertThat(
                        JwtAuthVersion.read(decoder.decode(token).getClaim("auth_version"))
                                .orElseThrow())
                .isEqualTo(alice.getAuthVersion());
        jdbc.update("update auth_user set username='renamed' where id=?", alice.getId());
        me(token).andExpect(status().isOk()).andExpect(jsonPath("username").value("renamed"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        me("invalid").andExpect(status().isUnauthorized());
        me(login(admin)).andExpect(status().isOk()).andExpect(jsonPath("role").value("ADMIN"));
    }

    @Test
    void logoutInvalidatesAllOwnTokensButNotOtherUsersAndReloginSucceeds() throws Exception {
        String one = login(alice), two = login(alice), other = login(bob);
        assertThat(one).isNotEqualTo(two);
        logout(one).andExpect(status().isNoContent()).andExpect(content().string(""));
        me(one).andExpect(status().isUnauthorized());
        me(two).andExpect(status().isUnauthorized());
        logout(two).andExpect(status().isUnauthorized());
        me(other).andExpect(status().isOk());
        long current = accounts.findById(alice.getId()).orElseThrow().getAuthVersion();
        assertThat(current).isEqualTo(alice.getAuthVersion() + 1);
        String fresh = login(alice);
        assertThat(
                        JwtAuthVersion.read(decoder.decode(fresh).getClaim("auth_version"))
                                .orElseThrow())
                .isEqualTo(current);
        me(fresh).andExpect(status().isOk());
    }

    @Test
    void logoutCannotTargetAnotherAccountThroughItsBodyOrActorHeader() throws Exception {
        String aliceToken = login(alice), bobToken = login(bob);
        mvc.perform(
                        post("/api/auth/logout")
                                .header("Authorization", "Bearer " + aliceToken)
                                .header("X-User-Id", bob.getId())
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("id", bob.getId(), "auth_version", 999))))
                .andExpect(status().isNoContent());
        me(aliceToken).andExpect(status().isUnauthorized());
        me(bobToken).andExpect(status().isOk());
        assertThat(accounts.findById(bob.getId()).orElseThrow().getAuthVersion())
                .isEqualTo(bob.getAuthVersion());
    }

    @Test
    void disabledOrUnverifiedOrDeletedAccountCannotUseAnExistingToken() throws Exception {
        String disabled = login(alice), unverified = login(bob), deleted = login(admin);
        alice.setEnabled(false);
        accounts.saveAndFlush(alice);
        jdbc.update("update auth_user set email_verified=false where id=?", bob.getId());
        jdbc.update("delete from auth_user where id=?", admin.getId());
        for (String token : List.of(disabled, unverified, deleted)) {
            me(token).andExpect(status().isUnauthorized());
            logout(token).andExpect(status().isUnauthorized());
        }
        alice.setEnabled(true);
        accounts.saveAndFlush(alice);
        me(disabled).andExpect(status().isUnauthorized());
        me(login(alice)).andExpect(status().isOk());
    }

    @Test
    void roleChangesAreReadFromDatabaseEvenWithoutAnExplicitVersionBump() throws Exception {
        String token = login(alice);
        jdbc.update("update auth_user set role='ADMIN' where id=?", alice.getId());
        me(token).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/transports")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"reference\":\"DENIED\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from transport", Long.class)).isZero();
        me(login(alice)).andExpect(status().isOk()).andExpect(jsonPath("role").value("ADMIN"));
    }

    String signed(Object version, boolean includeVersion, List<String> roles) {
        var claims =
                JwtClaimsSet.builder()
                        .subject(alice.getId().toString())
                        .issuer("https://auth.test")
                        .audience(List.of("transport-test"))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .claim("roles", roles);
        if (includeVersion) claims.claim("auth_version", version);
        return encoder.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    @Test
    void oldAndMalformedVersionClaimsFailBeforeAccountLookup() throws Exception {
        clearInvocations(store);
        me(signed(null, false, List.of("ROLE_USER"))).andExpect(status().isUnauthorized());
        for (Object version :
                List.of(-1L, 1.0, "1", true, new java.math.BigInteger("9223372036854775808")))
            me(signed(version, true, List.of("ROLE_USER"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(store);
        me(signed(alice.getAuthVersion() + 1, true, List.of("ROLE_USER")))
                .andExpect(status().isUnauthorized());
        me(signed(alice.getAuthVersion(), true, List.of("ROLE_ADMIN")))
                .andExpect(status().isUnauthorized());
        me(signed(alice.getAuthVersion(), true, List.of())).andExpect(status().isUnauthorized());
    }

    @Test
    void accountDatabaseFailureReturns503WithoutExecutingBusinessWrites() throws Exception {
        String token = login(admin);
        doThrow(new DataAccessResourceFailureException("test database detail must not leak"))
                .when(store)
                .findById(admin.getId());
        me(token)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("message").value("Authentication service unavailable"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(
                        post("/api/transports")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"reference\":\"DENIED\"}"))
                .andExpect(status().isServiceUnavailable());
        logout(token).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from transport", Long.class)).isZero();
        assertThat(accounts.findById(admin.getId()).orElseThrow().getAuthVersion())
                .isEqualTo(admin.getAuthVersion());
        doCallRealMethod().when(store).findById(admin.getId());
        me(token).andExpect(status().isOk());
    }

    @Test
    void transactionFailureAfterLiveCheckReturns503AndDoesNotReportSuccessfulLogout()
            throws Exception {
        String token = login(alice);
        doThrow(new CannotCreateTransactionException("private connection details"))
                .when(transactions)
                .getTransaction(any(TransactionDefinition.class));
        logout(token)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("message").value("Authentication service unavailable"));
        doCallRealMethod().when(transactions).getTransaction(any(TransactionDefinition.class));
        me(token).andExpect(status().isOk());
        assertThat(accounts.findById(alice.getId()).orElseThrow().getAuthVersion())
                .isEqualTo(alice.getAuthVersion());
    }

    @Test
    void tokenIssuanceNeverRereadsANewerVersionAfterLogout() throws Exception {
        String existing = login(alice);
        var captured = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            // Pause after credentials are validated, before the controller can
                            // issue a JWT.
                            var authenticated = invocation.callRealMethod();
                            captured.countDown();
                            if (!release.await(15, TimeUnit.SECONDS))
                                throw new IllegalStateException("Timed out waiting for logout");
                            return authenticated;
                        })
                .when(authenticationManager)
                .authenticate(any(org.springframework.security.core.Authentication.class));
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> login(alice));
            try {
                assertThat(captured.await(15, TimeUnit.SECONDS)).isTrue();
                logout(existing).andExpect(status().isNoContent());
            } finally {
                release.countDown();
            }
            String raced = pending.get(15, TimeUnit.SECONDS);
            assertThat(
                            JwtAuthVersion.read(decoder.decode(raced).getClaim("auth_version"))
                                    .orElseThrow())
                    .isEqualTo(alice.getAuthVersion());
            me(raced).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void simultaneousLogoutsSerializeAndLeaveAllOldSessionsInvalid() throws Exception {
        String one = login(alice), two = login(alice);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first =
                    pool.submit(
                            () -> {
                                start.await();
                                return logout(one).andReturn().getResponse().getStatus();
                            });
            var second =
                    pool.submit(
                            () -> {
                                start.await();
                                return logout(two).andReturn().getResponse().getStatus();
                            });
            start.countDown();
            var results =
                    List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results).contains(204);
            assertThat(results).allMatch(status -> status == 204 || status == 401);
        }
        me(one).andExpect(status().isUnauthorized());
        me(two).andExpect(status().isUnauthorized());
        assertThat(accounts.findById(alice.getId()).orElseThrow().getAuthVersion())
                .isEqualTo(alice.getAuthVersion() + 1);
    }

    @Test
    void anUnexpectedNonBearerAuthenticationCannotBypassLiveValidation() throws Exception {
        var identity =
                org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.user(alice.getId().toString())
                        .roles("USER");
        mvc.perform(get("/api/auth/me").with(identity)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs/1").with(identity)).andExpect(status().isUnauthorized());
    }
}
