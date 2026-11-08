package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;

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
            "auth.registration.enabled=true",
            "auth.registration.max-attempts=1000",
            "auth.login.max-attempts=1000"
        })
@AutoConfigureMockMvc
@Import(RegistrationIntegrationTest.Config.class)
class RegistrationIntegrationTest {
    static class TestClock extends Clock {
        volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    static class Mailbox implements VerificationEmailSender {
        final BlockingQueue<VerificationEmail> attempts = new LinkedBlockingQueue<>();
        volatile boolean fail;

        public void send(VerificationEmail email) {
            boolean failure = fail;
            attempts.add(email);
            if (failure) throw new MailSendException("test delivery failure");
        }

        VerificationEmail next() throws Exception {
            var value = attempts.poll(10, TimeUnit.SECONDS);
            assertThat(value).isNotNull();
            return value;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }

        @Bean
        @Primary
        Mailbox mailbox() {
            return new Mailbox();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        TestPostgres.createDatabase("registration_test");
        String url = TestPostgres.INSTANCE.getJdbcUrl("postgres", "registration_test");
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
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired RegistrationService registration;
    @Autowired TestClock clock;
    @Autowired Mailbox mailbox;

    @Autowired
    @Qualifier("verificationMailExecutor")
    TaskExecutor executor;

    static final String PASSWORD = "registration-test-password";

    void idle() throws Exception {
        var barrier = new CompletableFuture<Void>();
        executor.execute(() -> barrier.complete(null));
        barrier.get(10, TimeUnit.SECONDS);
    }

    @BeforeEach
    void clean() throws Exception {
        idle();
        jdbc.execute("TRUNCATE transport, auth_user RESTART IDENTITY CASCADE");
        mailbox.attempts.clear();
        mailbox.fail = false;
        clock.now = Instant.parse("2026-01-01T00:00:00Z");
    }

    ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(
                post(path).contentType("application/json").content(json.writeValueAsString(body)));
    }

    String signup(String username, String email, String password) throws Exception {
        return postJson(
                        "/api/auth/register",
                        Map.of("username", username, "email", email, "password", password))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    void verify(String token, int expected) throws Exception {
        postJson("/api/auth/verify-email", Map.of("token", token)).andExpect(status().is(expected));
    }

    @Test
    void signupVerifyLoginKeepsUserUnassignedAndStoresOnlyHashes() throws Exception {
        signup("alice", " ALICE@Test.Invalid ", PASSWORD);
        var mail = mailbox.next();
        assertThat(mail.recipient()).isEqualTo("alice@test.invalid");
        var account = accounts.findByUsername("alice").orElseThrow();
        assertThat(account.getRole()).isEqualTo("USER");
        assertThat(account.isEmailVerified()).isFalse();
        assertThat(passwords.matches(PASSWORD, account.getPasswordHash())).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select token_hash from email_verification_token", String.class))
                .isEqualTo(RegistrationService.digest(mail.token()))
                .doesNotContain(mail.token());
        assertThat(
                        jdbc.queryForObject(
                                "select expires_at-issued_at=interval '24 hours' from"
                                    + " email_verification_token",
                                Boolean.class))
                .isTrue();
        postJson("/api/auth/login", Map.of("username", "alice", "password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("message").value("Invalid credentials"));
        verify(mail.token(), 200);
        verify(mail.token(), 400);
        assertThat(accounts.findByUsername("alice").orElseThrow().isEmailVerified()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from email_verification_token", Long.class))
                .isZero();
        var response =
                postJson("/api/auth/login", Map.of("username", "alice", "password", PASSWORD))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("expiresIn").value(900))
                        .andReturn();
        String token =
                json.readTree(response.getResponse().getContentAsString())
                        .get("accessToken")
                        .asText();
        var jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(account.getId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(jdbc.queryForObject("select count(*) from transport_assignment", Long.class))
                .isZero();
        mvc.perform(get("/api/jobs/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/transports")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"reference\":\"DENIED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateUsernameOrNormalizedEmailNeverChangesExistingIdentityOrCode() throws Exception {
        String generic = signup("alice", "alice@test.invalid", PASSWORD);
        var mail = mailbox.next();
        var before = accounts.findByUsername("alice").orElseThrow();
        assertThat(signup("alice", "attacker@test.invalid", "attacker-password-123"))
                .isEqualTo(generic);
        assertThat(signup("attacker", "ALICE@TEST.INVALID", "attacker-password-123"))
                .isEqualTo(generic);
        idle();
        assertThat(mailbox.attempts).isEmpty();
        assertThat(accounts.count()).isEqualTo(1);
        var after = accounts.findByUsername("alice").orElseThrow();
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
        assertThat(
                        jdbc.queryForObject(
                                "select token_hash from email_verification_token", String.class))
                .isEqualTo(RegistrationService.digest(mail.token()));
        verify(mail.token(), 200);
    }

    @Test
    void privilegeFieldsCannotGrantAdminVerificationOrAssignments() throws Exception {
        postJson(
                        "/api/auth/register",
                        Map.of(
                                "username",
                                "alice",
                                "email",
                                "alice@test.invalid",
                                "password",
                                PASSWORD,
                                "role",
                                "ADMIN",
                                "emailVerified",
                                true,
                                "enabled",
                                false,
                                "assignments",
                                List.of(1)))
                .andExpect(status().isAccepted());
        mailbox.next();
        var account = accounts.findByUsername("alice").orElseThrow();
        assertThat(account.getRole()).isEqualTo("USER");
        assertThat(account.isEmailVerified()).isFalse();
        assertThat(account.isEnabled()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from transport_assignment", Long.class))
                .isZero();
    }

    @Test
    void resendHasCooldownRotatesCodeAndUsesGenericResponses() throws Exception {
        signup("alice", "alice@test.invalid", PASSWORD);
        var first = mailbox.next();
        String generic =
                postJson("/api/auth/resend-verification", Map.of("email", "alice@test.invalid"))
                        .andExpect(status().isAccepted())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        idle();
        assertThat(mailbox.attempts).isEmpty();
        clock.advance(Duration.ofSeconds(60));
        postJson("/api/auth/resend-verification", Map.of("email", "ALICE@TEST.INVALID"))
                .andExpect(status().isAccepted());
        var second = mailbox.next();
        assertThat(second.token()).isNotEqualTo(first.token());
        verify(first.token(), 400);
        verify(second.token(), 200);
        for (String email : List.of("unknown@test.invalid", "alice@test.invalid"))
            assertThat(
                            postJson("/api/auth/resend-verification", Map.of("email", email))
                                    .andExpect(status().isAccepted())
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsString())
                    .isEqualTo(generic);
        idle();
        assertThat(mailbox.attempts).isEmpty();
    }

    @Test
    void expiredAndMalformedCodesAreRejectedWithoutVerifyingAccount() throws Exception {
        signup("alice", "alice@test.invalid", PASSWORD);
        var mail = mailbox.next();
        clock.advance(Duration.ofHours(24));
        for (String token : List.of(mail.token(), "invalid", "_".repeat(43))) verify(token, 400);
        assertThat(accounts.findByUsername("alice").orElseThrow().isEmailVerified()).isFalse();
        postJson("/api/auth/resend-verification", Map.of("email", "alice@test.invalid"))
                .andExpect(status().isAccepted());
        verify(mailbox.next().token(), 200);
    }

    @Test
    void disabledAccountsCannotVerifyLoginOrTriggerMail() throws Exception {
        signup("alice", "alice@test.invalid", PASSWORD);
        var mail = mailbox.next();
        var account = accounts.findByUsername("alice").orElseThrow();
        account.setEnabled(false);
        accounts.saveAndFlush(account);
        clock.advance(Duration.ofMinutes(1));
        verify(mail.token(), 400);
        postJson("/api/auth/login", Map.of("username", "alice", "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/resend-verification", Map.of("email", "alice@test.invalid"))
                .andExpect(status().isAccepted());
        idle();
        assertThat(mailbox.attempts).isEmpty();
    }

    @Test
    void smtpFailureKeepsPendingAccountAndResendRecovers() throws Exception {
        mailbox.fail = true;
        signup("alice", "alice@test.invalid", PASSWORD);
        var failed = mailbox.next();
        idle();
        assertThat(accounts.findByUsername("alice").orElseThrow().isEmailVerified()).isFalse();
        mailbox.fail = false;
        clock.advance(Duration.ofMinutes(1));
        postJson("/api/auth/resend-verification", Map.of("email", "alice@test.invalid"))
                .andExpect(status().isAccepted());
        var retry = mailbox.next();
        verify(failed.token(), 400);
        verify(retry.token(), 200);
    }

    @Test
    void invalidRequestsAndBcryptByteOverflowCannotCreateAccounts() throws Exception {
        for (Map<String, String> body :
                List.of(
                        Map.of(
                                "username",
                                "bad name",
                                "email",
                                "alice@test.invalid",
                                "password",
                                PASSWORD),
                        Map.of("username", "alice", "email", "not-email", "password", PASSWORD),
                        Map.of(
                                "username",
                                "alice",
                                "email",
                                "alice@test.invalid",
                                "password",
                                "short"),
                        Map.of(
                                "username",
                                "alice",
                                "email",
                                "alice@test.invalid",
                                "password",
                                "x".repeat(73)),
                        Map.of(
                                "username",
                                "alice",
                                "email",
                                "alice@test.invalid",
                                "password",
                                "😀".repeat(20))))
            postJson("/api/auth/register", body).andExpect(status().isBadRequest());
        assertThat(accounts.count()).isZero();
        signup("alice", "alice@test.invalid", "x".repeat(72));
        mailbox.next();
        signup("unicode", "unicode@test.invalid", "😀".repeat(15));
        mailbox.next();
        assertThat(accounts.count()).isEqualTo(2);
    }

    void registrationRace(boolean sameUsername) throws Exception {
        try (var pool = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 4; i++) {
                int index = i;
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return postJson(
                                                    "/api/auth/register",
                                                    Map.of(
                                                            "username",
                                                            sameUsername ? "same" : "user" + index,
                                                            "email",
                                                            sameUsername
                                                                    ? "user"
                                                                            + index
                                                                            + "@test.invalid"
                                                                    : "same@test.invalid",
                                                            "password",
                                                            PASSWORD))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            }
            start.countDown();
            for (var future : futures) assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(202);
        }
        idle();
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(mailbox.attempts).hasSize(1);
        verify(mailbox.next().token(), 200);
    }

    @Test
    void concurrentDuplicateEmailsCreateOnlyOneAccount() {
        assertThatCode(() -> registrationRace(false)).doesNotThrowAnyException();
    }

    @Test
    void concurrentDuplicateUsernamesCreateOnlyOneAccount() {
        assertThatCode(() -> registrationRace(true)).doesNotThrowAnyException();
    }

    @Test
    void verificationCodeHasExactlyOneSuccessfulConcurrentConsumer() throws Exception {
        signup("alice", "alice@test.invalid", PASSWORD);
        var mail = mailbox.next();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt =
                    () -> {
                        start.await();
                        return postJson("/api/auth/verify-email", Map.of("token", mail.token()))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    };
            var one = pool.submit(attempt);
            var two = pool.submit(attempt);
            start.countDown();
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        }
    }

    @Test
    void legacyAccountRequiresExplicitBackfillAndVerification() throws Exception {
        var legacy =
                accounts.saveAndFlush(new Account("legacy", passwords.encode(PASSWORD), "USER"));
        postJson("/api/auth/login", Map.of("username", "legacy", "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        jdbc.update(
                "update auth_user set email=? where id=?", "legacy@test.invalid", legacy.getId());
        postJson("/api/auth/resend-verification", Map.of("email", "legacy@test.invalid"))
                .andExpect(status().isAccepted());
        verify(mailbox.next().token(), 200);
        postJson("/api/auth/login", Map.of("username", "legacy", "password", PASSWORD))
                .andExpect(status().isOk());
        assertThat(accounts.findByUsername("legacy").orElseThrow().getId())
                .isEqualTo(legacy.getId());
    }
}
