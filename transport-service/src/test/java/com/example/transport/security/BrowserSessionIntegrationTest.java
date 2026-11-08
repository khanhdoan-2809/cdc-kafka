package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.transport.web.BrowserAuthController;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.*;

import java.sql.Timestamp;
import java.time.*;
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
            "auth.login.max-attempts=1000",
            "auth.browser.max-attempts=1000",
            "auth.browser.enabled=true",
            "auth.browser.allowed-origins=https://app.test.invalid"
        })
@AutoConfigureMockMvc
class BrowserSessionIntegrationTest {
    static final String ORIGIN = "https://app.test.invalid", PASSWORD = "browser-test-password";
    static final String REFRESH = BrowserAuthController.REFRESH_COOKIE, CSRF = "__Host-cdc-csrf";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        TestPostgres.createDatabase("browser_sessions_test");
        String url = TestPostgres.INSTANCE.getJdbcUrl("postgres", "browser_sessions_test");
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
    @MockitoSpyBean AccountRepository accounts;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder decoder;
    @MockitoSpyBean JwtService tokens;
    @Autowired BrowserSessionService sessions;
    @MockitoSpyBean AuthenticationManager authenticationManager;
    @MockitoSpyBean PlatformTransactionManager transactions;
    @MockitoSpyBean Clock clock;
    Account alice, bob;

    static class Browser {
        Cookie csrf, refresh;
        String token, access;
    }

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE transport, auth_user RESTART IDENTITY CASCADE");
        String hash = new BCryptPasswordEncoder(4).encode(PASSWORD);
        alice = fixture("alice", hash);
        bob = fixture("bob", hash);
    }

    Account fixture(String name, String hash) {
        var a = new Account(name, hash, "USER", name + "@test.invalid");
        a.verifyEmail();
        return accounts.saveAndFlush(a);
    }

    void csrf(Browser b) throws Exception {
        var request = get("/api/auth/csrf").secure(true).header("Origin", ORIGIN);
        if (b.csrf != null) request.cookie(b.csrf);
        var response =
                mvc.perform(request)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("headerName").value("X-CSRF-TOKEN"))
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn();
        Cookie cookie = cookie(response, CSRF);
        if (cookie != null) b.csrf = cookie;
        b.token = json.readTree(response.getResponse().getContentAsString()).get("token").asText();
    }

    Cookie cookie(MvcResult result, String name) {
        // Servlet 6 cookies carry SameSite as an attribute. MockMvc's header serializer omits
        // that attribute on plain Servlet Cookie objects; inspect the response cookie itself.
        Cookie actual = result.getResponse().getCookie(name);
        if (actual != null) return actual;
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .map(MockCookie::parse)
                .filter(c -> c.getName().equals(name))
                .reduce((a, b) -> b)
                .orElse(null);
    }

    MockHttpServletRequestBuilder request(String path, Browser b) {
        var request =
                post("/api/auth/" + path)
                        .secure(true)
                        .header("Origin", ORIGIN)
                        .header("X-CSRF-TOKEN", b.token)
                        .cookie(b.csrf);
        if (b.refresh != null) request.cookie(b.refresh);
        return request;
    }

    MockHttpServletRequestBuilder loginRequest(Browser b, Account a) throws Exception {
        return request("browser-login", b)
                .contentType("application/json")
                .content(
                        json.writeValueAsString(
                                Map.of("username", a.getUsername(), "password", PASSWORD)));
    }

    Browser login(Account a) throws Exception {
        var b = new Browser();
        csrf(b);
        var result =
                mvc.perform(loginRequest(b, a))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("expiresIn").value(900))
                        .andReturn();
        capture(b, result);
        b.csrf = cookie(result, CSRF);
        csrf(b);
        return b;
    }

    void capture(Browser b, MvcResult result) throws Exception {
        b.refresh = cookie(result, REFRESH);
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(3);
        b.access = body.get("accessToken").asText();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(b.refresh.getValue());
    }

    ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token));
    }

    long version(Account a) {
        return accounts.findById(a.getId()).orElseThrow().getAuthVersion();
    }

    long count() {
        return jdbc.queryForObject("select count(*) from browser_refresh_token", Long.class);
    }

    @Test
    void cookiesAreHostOnlySecureAndHttpOnlyAndSecretsAreHashed() throws Exception {
        var b = login(alice);
        for (Cookie cookie : List.of(b.csrf, b.refresh)) {
            assertThat(cookie.getName()).startsWith("__Host-");
            assertThat(cookie.getSecure()).isTrue();
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getDomain()).isNull();
            assertThat(
                            cookie instanceof MockCookie mock
                                    ? mock.getSameSite()
                                    : cookie.getAttribute("SameSite"))
                    .isEqualTo("Lax");
        }
        assertThat(b.refresh.getMaxAge()).isBetween(604798, 604800);
        assertThat(b.refresh.getValue()).matches("[A-Za-z0-9_-]{43}");
        var row = jdbc.queryForMap("select * from browser_refresh_token");
        assertThat(row.get("token_hash"))
                .isEqualTo(RegistrationService.digest(b.refresh.getValue()));
        assertThat(row.values()).doesNotContain(b.refresh.getValue());
        assertThat(row.get("auth_version")).isEqualTo(alice.getAuthVersion());
        assertThat(row.get("role")).isEqualTo("USER");
        me(b.access).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(b.refresh)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/csrf").secure(true).cookie(b.csrf))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void everyUnsafeBrowserEndpointRequiresCsrfAndExplicitAllowedOriginAndHttps() throws Exception {
        var b = login(alice);
        for (String path : List.of("browser-login", "refresh", "browser-logout")) {
            mvc.perform(
                            post("/api/auth/" + path)
                                    .secure(true)
                                    .header("Origin", ORIGIN)
                                    .cookie(b.csrf, b.refresh))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/api/auth/" + path)
                                    .secure(true)
                                    .header("X-CSRF-TOKEN", b.token)
                                    .cookie(b.csrf, b.refresh))
                    .andExpect(status().isForbidden());
            mvc.perform(request(path, b).header("Origin", "https://evil.test.invalid"))
                    .andExpect(status().isForbidden());
            mvc.perform(request(path, b).secure(false).header("X-Forwarded-Proto", "https"))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            request(path, b)
                                    .with(
                                            r -> {
                                                r.removeHeader("X-CSRF-TOKEN");
                                                r.addHeader("X-CSRF-TOKEN", "wrong");
                                                return r;
                                            }))
                    .andExpect(status().isForbidden());
        }
        assertThat(count()).isEqualTo(1);
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion());
        assertThat(
                        jdbc.queryForObject(
                                "select consumed_at is null from browser_refresh_token",
                                Boolean.class))
                .isTrue();
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isForbidden());
        mvc.perform(
                        options("/api/auth/refresh")
                                .secure(true)
                                .header("Origin", ORIGIN)
                                .header("Access-Control-Request-Method", "POST")
                                .header(
                                        "Access-Control-Request-Headers",
                                        "X-CSRF-TOKEN,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(
                        options("/api/auth/refresh")
                                .secure(true)
                                .header("Origin", "https://evil.test.invalid")
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        options("/api/auth/me")
                                .secure(true)
                                .header("Origin", ORIGIN)
                                .header("Access-Control-Request-Method", "GET")
                                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @Test
    void adminBrowserRoleIsPreservedAndInvalidCredentialsCreateNoSession() throws Exception {
        var pending = new Browser();
        csrf(pending);
        mvc.perform(
                        request("browser-login", pending)
                                .contentType("application/json")
                                .content("{\"username\":\"alice\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(count()).isZero();
        jdbc.update("update auth_user set role='ADMIN' where id=?", alice.getId());
        var b = login(alice);
        var result = mvc.perform(request("refresh", b)).andExpect(status().isOk()).andReturn();
        capture(b, result);
        assertThat(decoder.decode(b.access).getClaimAsStringList("roles"))
                .containsExactly("ROLE_ADMIN");
        me(b.access).andExpect(status().isOk()).andExpect(jsonPath("role").value("ADMIN"));
    }

    @Test
    void loginRotatesCsrfAndOldTokenWithNewCookieFails() throws Exception {
        var b = new Browser();
        csrf(b);
        String old = b.token;
        Cookie oldCookie = b.csrf;
        var result = mvc.perform(loginRequest(b, alice)).andExpect(status().isOk()).andReturn();
        b.refresh = cookie(result, REFRESH);
        b.csrf = cookie(result, CSRF);
        assertThat(b.csrf.getValue()).isNotEqualTo(oldCookie.getValue());
        mvc.perform(request("refresh", b)).andExpect(status().isForbidden());
        csrf(b);
        assertThat(b.token).isNotEqualTo(old);
        mvc.perform(request("refresh", b)).andExpect(status().isOk());
    }

    @Test
    void rotationWorksWithoutBearerAndNeverExtendsFamilyExpiry() throws Exception {
        var b = login(alice);
        Cookie old = b.refresh;
        var initial = jdbc.queryForMap("select family_id,expires_at from browser_refresh_token");
        var result =
                mvc.perform(
                                request("refresh", b)
                                        .header("Authorization", "Bearer expired-or-invalid"))
                        .andExpect(status().isOk())
                        .andReturn();
        capture(b, result);
        assertThat(b.refresh.getValue()).isNotEqualTo(old.getValue());
        assertThat(count()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select count(distinct family_id) from browser_refresh_token",
                                Long.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForList(
                                "select expires_at from browser_refresh_token", Timestamp.class))
                .containsOnly((Timestamp) initial.get("expires_at"));
        assertThat(
                        jdbc.queryForObject(
                                "select consumed_at is not null from browser_refresh_token where"
                                        + " token_hash=?",
                                Boolean.class,
                                RegistrationService.digest(old.getValue())))
                .isTrue();
        me(b.access).andExpect(status().isOk());
    }

    @Test
    void replayCommitsAccountWideRevocationAndStaleReplayCannotLogOutANewSession()
            throws Exception {
        var first = login(alice);
        var second = login(alice);
        var other = login(bob);
        Cookie old = first.refresh;
        var result = mvc.perform(request("refresh", first)).andExpect(status().isOk()).andReturn();
        capture(first, result);
        String successor = first.access;
        first.refresh = old;
        mvc.perform(request("refresh", first))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        header().stringValues(
                                        "Set-Cookie",
                                        org.hamcrest.Matchers.hasItems(
                                                org.hamcrest.Matchers.containsString(
                                                        REFRESH + "=;"),
                                                org.hamcrest.Matchers.containsString(
                                                        CSRF + "=;"))));
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion() + 1);
        me(successor).andExpect(status().isUnauthorized());
        me(second.access).andExpect(status().isUnauthorized());
        mvc.perform(request("refresh", second)).andExpect(status().isUnauthorized());
        var fresh = login(alice);
        mvc.perform(request("refresh", first)).andExpect(status().isUnauthorized());
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion() + 1);
        me(fresh.access).andExpect(status().isOk());
        me(other.access).andExpect(status().isOk());
    }

    @Test
    void browserLogoutNeedsNoAccessTokenInvalidatesAllDevicesAndClearsCookies() throws Exception {
        var first = login(alice);
        var second = login(alice);
        var other = login(bob);
        var result =
                mvc.perform(
                                request("browser-logout", first)
                                        .header("Authorization", "Bearer expired"))
                        .andExpect(status().isNoContent())
                        .andExpect(content().string(""))
                        .andReturn();
        assertThat(cookie(result, REFRESH).getMaxAge()).isZero();
        assertThat(cookie(result, CSRF).getMaxAge()).isZero();
        me(first.access).andExpect(status().isUnauthorized());
        me(second.access).andExpect(status().isUnauthorized());
        mvc.perform(request("refresh", second)).andExpect(status().isUnauthorized());
        mvc.perform(request("browser-logout", first)).andExpect(status().isUnauthorized());
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion() + 1);
        me(other.access).andExpect(status().isOk());
    }

    @Test
    void currentVersionReplayStillRevokesIfSecurityFieldsWereChangedWithoutAVersionBump()
            throws Exception {
        var b = login(alice);
        mvc.perform(request("refresh", b)).andExpect(status().isOk());
        jdbc.update("update auth_user set role='ADMIN' where id=?", alice.getId());
        mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion() + 1);
        mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion() + 1);
    }

    @Test
    void apiLoginRemainsCookieFreeAndBearerLogoutAlsoRevokesRefresh() throws Exception {
        var b = login(alice);
        var result =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "username",
                                                                "alice",
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andExpect(header().doesNotExist("Set-Cookie"))
                        .andReturn();
        String api =
                json.readTree(result.getResponse().getContentAsString())
                        .get("accessToken")
                        .asText();
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + api))
                .andExpect(status().isNoContent());
        mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void expiredFamiliesRejectWithoutRevocationAndCleanupRetainsUnexpiredConsumedHashes()
            throws Exception {
        var old = login(alice);
        Cookie consumed = old.refresh;
        var rotated = mvc.perform(request("refresh", old)).andExpect(status().isOk()).andReturn();
        capture(old, rotated);
        sessions.cleanupExpired();
        assertThat(count()).isEqualTo(2);
        Instant future = Instant.now().plus(Duration.ofDays(8));
        doReturn(future).when(clock).instant();
        mvc.perform(request("refresh", old)).andExpect(status().isUnauthorized());
        old.refresh = consumed;
        mvc.perform(request("refresh", old)).andExpect(status().isUnauthorized());
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion());
        sessions.cleanupExpired();
        assertThat(count()).isZero();
    }

    @Test
    void accessExpiryIsCappedByFamilyDeadline() throws Exception {
        var b = login(alice);
        Instant deadline =
                Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        jdbc.update("update browser_refresh_token set expires_at=?", Timestamp.from(deadline));
        var result = mvc.perform(request("refresh", b)).andExpect(status().isOk()).andReturn();
        capture(b, result);
        assertThat(decoder.decode(b.access).getExpiresAt()).isEqualTo(deadline);
        assertThat(
                        json.readTree(result.getResponse().getContentAsString())
                                .get("expiresIn")
                                .asLong())
                .isBetween(1L, 60L);
    }

    @Test
    void accountStatusRoleVersionAndDeletionAreRecheckedOnRefresh() throws Exception {
        for (String update :
                List.of(
                        "enabled=false",
                        "email_verified=false",
                        "role='ADMIN'",
                        "auth_version=auth_version+1")) {
            var b = login(alice);
            jdbc.update("update auth_user set " + update + " where id=?", alice.getId());
            long current = version(alice);
            mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
            assertThat(count()).isEqualTo(1);
            assertThat(version(alice)).isEqualTo(current);
            jdbc.update(
                    "update auth_user set enabled=true,email_verified=true,role='USER' where id=?",
                    alice.getId());
            jdbc.update("delete from browser_refresh_token");
        }
        var b = login(alice);
        accounts.deleteById(alice.getId());
        mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
        assertThat(count()).isZero();
    }

    @Test
    void invalidMissingOrDuplicateCookieCannotSelectOrInvalidateAnAccount() throws Exception {
        var b = login(alice);
        Cookie real = b.refresh;
        for (String invalid : List.of("malformed", "A".repeat(43))) {
            b.refresh = new Cookie(REFRESH, invalid);
            mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
        }
        b.refresh = null;
        mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
        b.refresh = real;
        mvc.perform(request("refresh", b).cookie(new Cookie(REFRESH, "A".repeat(43))))
                .andExpect(status().isUnauthorized());
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion());
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void databaseFailureReturns503WithoutIssuingOrConsumingCredentials() throws Exception {
        var b = login(alice);
        doThrow(new CannotCreateTransactionException("private connection details"))
                .when(transactions)
                .getTransaction(any(TransactionDefinition.class));
        for (String path : List.of("refresh", "browser-logout"))
            mvc.perform(request(path, b))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().doesNotExist("Set-Cookie"))
                    .andExpect(jsonPath("message").value("Authentication service unavailable"));
        mvc.perform(loginRequest(b, alice))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Set-Cookie"));
        doCallRealMethod().when(transactions).getTransaction(any(TransactionDefinition.class));
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion());
        assertThat(count()).isEqualTo(1);
        mvc.perform(request("refresh", b)).andExpect(status().isOk());
    }

    @Test
    void databaseLookupFailureFailsClosedAndRecoveryCanRetryTheSameToken() throws Exception {
        var b = login(alice);
        doThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "private database details"))
                .when(accounts)
                .lockById(alice.getId());
        mvc.perform(request("refresh", b))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("message").value("Authentication service unavailable"));
        assertThat(count()).isEqualTo(1);
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion());
        reset(accounts);
        mvc.perform(request("refresh", b)).andExpect(status().isOk());
    }

    @Test
    void expiryBoundaryRejectsWithoutRevokingAccount() throws Exception {
        var b = login(alice);
        Instant expiry =
                jdbc.queryForObject("select expires_at from browser_refresh_token", Timestamp.class)
                        .toInstant();
        doReturn(expiry).when(clock).instant();
        mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
        assertThat(count()).isEqualTo(1);
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion());
    }

    @Test
    void signingFailureRollsBackConsumptionAndDoesNotSetCookies() throws Exception {
        var b = login(alice);
        doThrow(
                        new org.springframework.security.oauth2.jwt.JwtEncodingException(
                                "Signing unavailable"))
                .when(tokens)
                .generateTokenUntil(
                        anyString(), anyString(), anyList(), anyLong(), any(Instant.class));
        mvc.perform(request("refresh", b))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(count()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select consumed_at is null from browser_refresh_token",
                                Boolean.class))
                .isTrue();
        doCallRealMethod()
                .when(tokens)
                .generateTokenUntil(
                        anyString(), anyString(), anyList(), anyLong(), any(Instant.class));
        mvc.perform(request("refresh", b)).andExpect(status().isOk());
    }

    @Test
    void browserLoginCannotUpgradeCredentialSnapshotAfterLogout() throws Exception {
        var existing = login(alice);
        var pending = new Browser();
        csrf(pending);
        var validated = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            var authenticated = invocation.callRealMethod();
                            validated.countDown();
                            if (!release.await(15, TimeUnit.SECONDS))
                                throw new IllegalStateException("Timeout waiting for logout");
                            return authenticated;
                        })
                .when(authenticationManager)
                .authenticate(any(Authentication.class));
        try (var pool = Executors.newSingleThreadExecutor()) {
            var login = pool.submit(() -> mvc.perform(loginRequest(pending, alice)).andReturn());
            try {
                assertThat(validated.await(15, TimeUnit.SECONDS)).isTrue();
                mvc.perform(request("browser-logout", existing)).andExpect(status().isNoContent());
            } finally {
                release.countDown();
            }
            var result = login.get(15, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
        }
        assertThat(count()).isEqualTo(1);
        me(existing.access).andExpect(status().isUnauthorized());
    }

    @Test
    void simultaneousRefreshesCauseReplayRevocationEvenForTheWinner() throws Exception {
        var b = login(alice);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<MvcResult> action =
                    () -> {
                        start.await();
                        return mvc.perform(request("refresh", b)).andReturn();
                    };
            var one = pool.submit(action);
            var two = pool.submit(action);
            start.countDown();
            var results = List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().map(r -> r.getResponse().getStatus()).toList())
                    .containsExactlyInAnyOrder(200, 401);
            capture(
                    b,
                    results.stream()
                            .filter(r -> r.getResponse().getStatus() == 200)
                            .findFirst()
                            .orElseThrow());
            me(b.access).andExpect(status().isUnauthorized());
            mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
        }
        assertThat(version(alice)).isEqualTo(alice.getAuthVersion() + 1);
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void refreshAndLogoutSerializeWithoutLeavingAnyUsableSession() throws Exception {
        for (boolean bearer : List.of(false, true)) {
            var b = login(alice);
            long before = version(alice);
            var start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var refresh =
                        pool.submit(
                                () -> {
                                    start.await();
                                    return mvc.perform(request("refresh", b)).andReturn();
                                });
                var logout =
                        pool.submit(
                                () -> {
                                    start.await();
                                    return mvc.perform(
                                                    bearer
                                                            ? post("/api/auth/logout")
                                                                    .header(
                                                                            "Authorization",
                                                                            "Bearer " + b.access)
                                                            : request("browser-logout", b))
                                            .andReturn();
                                });
                start.countDown();
                var refreshed = refresh.get(15, TimeUnit.SECONDS);
                int loggedOut = logout.get(15, TimeUnit.SECONDS).getResponse().getStatus();
                assertThat(refreshed.getResponse().getStatus()).isIn(200, 401);
                assertThat(loggedOut).isIn(204, 401);
                me(b.access).andExpect(status().isUnauthorized());
                if (refreshed.getResponse().getStatus() == 200) {
                    capture(b, refreshed);
                    me(b.access).andExpect(status().isUnauthorized());
                    mvc.perform(request("refresh", b)).andExpect(status().isUnauthorized());
                }
            }
            assertThat(version(alice)).isEqualTo(before + 1);
        }
    }
}
