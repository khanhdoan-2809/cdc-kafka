package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.transport.repository.TransportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "jwt.issuer=https://auth.test",
            "jwt.audience=transport-test",
            "jwt.ttl=PT15M",
            "monitoring.password=test-monitoring-password",
            "auth.login.max-attempts=1000",
            "auth.bootstrap.username=",
            "auth.bootstrap.password="
        })
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired JwtService tokens;
    @Autowired TransportRepository transports;
    Account user;
    Account admin;
    static Path keys;

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) throws Exception {
        keys = Files.createTempDirectory("auth-test-keys");
        keys.toFile().deleteOnExit();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path privateKey = keys.resolve("private.pem"), publicKey = keys.resolve("public.pem");
        pem(privateKey, "PRIVATE KEY", pair.getPrivate().getEncoded());
        pem(publicKey, "PUBLIC KEY", pair.getPublic().getEncoded());
        registry.add("jwt.private-key", () -> privateKey.toUri().toString());
        registry.add("jwt.public-key", () -> publicKey.toUri().toString());
    }

    static void pem(Path file, String type, byte[] bytes) throws Exception {
        Files.writeString(
                file,
                "-----BEGIN "
                        + type
                        + "-----\n"
                        + Base64.getEncoder().encodeToString(bytes)
                        + "\n-----END "
                        + type
                        + "-----\n");
        file.toFile().deleteOnExit();
    }

    @BeforeEach
    void setup() {
        accounts.deleteAll();
        user =
                accounts.saveAndFlush(
                        verified(
                                new Account(
                                        "user",
                                        passwords.encode("user-test-password"),
                                        "USER",
                                        "user@test.invalid")));
        admin =
                accounts.saveAndFlush(
                        verified(
                                new Account(
                                        "admin",
                                        passwords.encode("admin-test-password"),
                                        "ADMIN",
                                        "admin@test.invalid")));
    }

    static Account verified(Account account) {
        account.verifyEmail();
        return account;
    }

    String login(String username, String password) throws Exception {
        var result =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "username",
                                                                username,
                                                                "password",
                                                                password))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("tokenType").value("Bearer"))
                        .andExpect(jsonPath("expiresIn").value(900))
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void registrationIsDisabledByDefault() throws Exception {
        long before = accounts.count();
        mvc.perform(
                        post("/api/auth/register")
                                .contentType("application/json")
                                .content(
                                        "{\"username\":\"new-user\",\"email\":\"new@test.invalid\",\"password\":\"valid-long-password\"}"))
                .andExpect(status().isNotFound());
        assertThat(accounts.count()).isEqualTo(before);
    }

    @Test
    void loginWithoutActorReturnsVerifiableToken() throws Exception {
        var token = decoder.decode(login("user", "user-test-password"));
        assertThat(token.getSubject()).isEqualTo(user.getId().toString());
        assertThat(token.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(token.getClaimAsString("username")).isEqualTo("user");
    }

    @Test
    void invalidUnknownAndDisabledAccountsHaveSameResponse() throws Exception {
        user.setEnabled(false);
        accounts.saveAndFlush(user);
        for (String username : List.of("admin", "unknown", "user")) {
            mvc.perform(
                            post("/api/auth/login")
                                    .contentType("application/json")
                                    .content(
                                            json.writeValueAsString(
                                                    Map.of(
                                                            "username",
                                                            username,
                                                            "password",
                                                            "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("message").value("Invalid credentials"));
        }
        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        "{\"username\":\"user\",\"password\":\"user-test-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("message").value("Invalid credentials"));
    }

    @Test
    void loginValidatesBlankAndMalformedBodies() throws Exception {
        for (String body : List.of("{}", "{", "{\"username\":\"u\",\"password\":\"\"}"))
            mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("message").value("Invalid login request"));
    }

    @Test
    void writesUseSubjectAndIgnoreSpoofedActorHeader() throws Exception {
        String bearer = login("admin", "admin-test-password");
        var response =
                mvc.perform(
                                post("/api/transports")
                                        .header("Authorization", "Bearer " + bearer)
                                        .header("X-User-Id", "spoofed-admin")
                                        .contentType("application/json")
                                        .content(
                                                "{\"reference\":\"AUTH-"
                                                        + UUID.randomUUID()
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        var persisted = transports.findById(id).orElseThrow();
        assertThat(persisted.getCreatedBy()).isEqualTo(admin.getId().toString());
        assertThat(persisted.getUpdatedBy()).isEqualTo(admin.getId().toString());
        var secondAdmin =
                accounts.saveAndFlush(
                        verified(
                                new Account(
                                        "second-admin",
                                        passwords.encode("second-admin-test-password"),
                                        "ADMIN",
                                        "second-admin@test.invalid")));
        String adminBearer = login("second-admin", "second-admin-test-password");
        mvc.perform(
                        patch("/api/transports/{id}/status", id)
                                .header("Authorization", "Bearer " + adminBearer)
                                .contentType("application/json")
                                .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        assertThat(transports.findById(id).orElseThrow().getUpdatedBy())
                .isEqualTo(secondAdmin.getId().toString());
        assertThat(transports.findById(id).orElseThrow().getCreatedBy())
                .isEqualTo(admin.getId().toString());
    }

    @Test
    void actorHeaderCannotAuthenticateOrWrite() throws Exception {
        long count = transports.count();
        mvc.perform(
                        post("/api/transports")
                                .header("X-User-Id", "admin")
                                .contentType("application/json")
                                .content("{\"reference\":\"UNAUTHORIZED\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(transports.count()).isEqualTo(count);
    }

    @Test
    void unassignedUserReadsAreHiddenAndAdminReadsRemainAllowed() throws Exception {
        String userToken = login("user", "user-test-password");
        mvc.perform(get("/api/jobs/123").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("error").value("Not Found"));
        // An admin passes authorization; use a real transport to obtain a successful response.
        String adminToken = login("admin", "admin-test-password");
        var created =
                mvc.perform(
                                post("/api/transports")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType("application/json")
                                        .content(
                                                "{\"reference\":\"ADMIN-"
                                                        + UUID.randomUUID()
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(
                        get("/api/transports/{id}/jobs", id)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    String signed(String issuer, String audience, Instant expiry, JwsAlgorithm algorithm) {
        var claims =
                JwtClaimsSet.builder()
                        .claim("auth_version", user.getAuthVersion())
                        .subject(user.getId().toString())
                        .issuer(issuer)
                        .audience(List.of(audience))
                        .issuedAt(Instant.now().minusSeconds(7200))
                        .expiresAt(expiry)
                        .claim("roles", List.of("ROLE_USER"))
                        .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(), claims))
                .getTokenValue();
    }

    @Test
    void missingExpiredTamperedWrongIssuerAudienceAndAlgorithmReturn401() throws Exception {
        mvc.perform(get("/api/jobs/123")).andExpect(status().isUnauthorized());
        var valid =
                tokens.generateToken(
                        user.getId().toString(),
                        "user",
                        List.of("ROLE_USER"),
                        user.getAuthVersion());
        var parts = valid.split("\\.");
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 1;
        String tampered =
                parts[0]
                        + "."
                        + parts[1]
                        + "."
                        + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        for (String token :
                List.of(
                        tampered,
                        "garbage",
                        signed(
                                "https://auth.test",
                                "transport-test",
                                Instant.now().minusSeconds(120),
                                SignatureAlgorithm.RS256),
                        signed(
                                "https://wrong.test",
                                "transport-test",
                                Instant.now().plusSeconds(600),
                                SignatureAlgorithm.RS256),
                        signed(
                                "https://auth.test",
                                "wrong",
                                Instant.now().plusSeconds(600),
                                SignatureAlgorithm.RS256),
                        signed(
                                "https://auth.test",
                                "transport-test",
                                Instant.now().plusSeconds(600),
                                SignatureAlgorithm.RS512))) {
            mvc.perform(get("/api/jobs/123").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("error").value("Unauthorized"));
        }
    }

    @Test
    void missingExpiryAndInvalidSubjectAreRejected() throws Exception {
        for (boolean missingExpiry : List.of(true, false)) {
            var builder =
                    JwtClaimsSet.builder()
                            .claim("auth_version", user.getAuthVersion())
                            .issuer("https://auth.test")
                            .audience(List.of("transport-test"))
                            .subject(missingExpiry ? user.getId().toString() : "not-a-user-id")
                            .issuedAt(Instant.now());
            if (!missingExpiry) builder.expiresAt(Instant.now().plusSeconds(600));
            var token =
                    encoder.encode(
                                    JwtEncoderParameters.from(
                                            JwsHeader.with(SignatureAlgorithm.RS256).build(),
                                            builder.build()))
                            .getTokenValue();
            mvc.perform(get("/api/jobs/123").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void healthIsPublicAndMonitoringCredentialsAreIsolated() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/actuator/prometheus")
                                .with(httpBasic("monitoring", "test-monitoring-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/jobs/123").with(httpBasic("monitoring", "test-monitoring-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        "{\"username\":\"monitoring\",\"password\":\"test-monitoring-password\"}"))
                .andExpect(status().isUnauthorized());
    }
}
