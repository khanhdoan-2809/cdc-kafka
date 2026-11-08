package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.transport.application.*;
import com.example.transport.application.context.*;
import com.example.transport.domain.container.*;
import com.example.transport.domain.job.*;
import com.example.transport.domain.transport.*;
import com.example.transport.web.context.RequestContextFilterCustom;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.request.*;

import java.util.*;
import java.util.function.Supplier;

@SpringBootTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "jwt.issuer=https://auth.test",
            "jwt.audience=transport-test",
            "jwt.ttl=PT15M",
            "monitoring.password=test-monitoring-password",
            "auth.bootstrap.username=",
            "auth.bootstrap.password="
        })
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        TestPostgres.createDatabase("authz_test");
        String url = TestPostgres.INSTANCE.getJdbcUrl("postgres", "authz_test");
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
    @Autowired JwtService tokens;
    @Autowired JwtDecoder decoder;
    @Autowired ObjectMapper json;
    @Autowired TransportService transports;
    @Autowired JobService jobs;
    @Autowired ContainerService containers;
    @Autowired TransportHierarchyService hierarchy;
    Account admin, alice, bob;
    String adminToken, aliceToken, bobToken;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE transport, auth_user RESTART IDENTITY CASCADE");
        admin =
                accounts.saveAndFlush(
                        AuthenticationIntegrationTest.verified(
                                new Account(
                                        "admin",
                                        "unused-test-password",
                                        "ADMIN",
                                        "admin@test.invalid")));
        alice =
                accounts.saveAndFlush(
                        AuthenticationIntegrationTest.verified(
                                new Account(
                                        "alice",
                                        "unused-test-password",
                                        "USER",
                                        "alice@test.invalid")));
        bob =
                accounts.saveAndFlush(
                        AuthenticationIntegrationTest.verified(
                                new Account(
                                        "bob",
                                        "unused-test-password",
                                        "USER",
                                        "bob@test.invalid")));
        adminToken = token(admin);
        aliceToken = token(alice);
        bobToken = token(bob);
        for (long transport : List.of(10L, 20L, 30L)) {
            jdbc.update(
                    "INSERT INTO transport"
                            + " (id,reference,status,created_at,updated_at,created_by,updated_by)"
                            + " OVERRIDING SYSTEM VALUE VALUES"
                            + " (?,?,'CREATED',now(),now(),'fixture','fixture')",
                    transport,
                    "T-" + transport);
            jdbc.update(
                    "INSERT INTO job"
                        + " (id,transport_id,reference,status,created_at,updated_at,created_by,updated_by)"
                        + " OVERRIDING SYSTEM VALUE VALUES (?, ?, ?,"
                        + " 'CREATED',now(),now(),'fixture','fixture')",
                    transport + 1,
                    transport,
                    "J-" + transport);
            jdbc.update(
                    "INSERT INTO container"
                        + " (id,job_id,container_number,status,created_at,updated_at,created_by,updated_by)"
                        + " OVERRIDING SYSTEM VALUE VALUES (?, ?, ?,"
                        + " 'CREATED',now(),now(),'fixture','fixture')",
                    transport + 2,
                    transport + 1,
                    "C-" + transport);
        }
        grant(10, alice);
        grant(20, bob);
    }

    String token(Account user) {
        return tokens.generateToken(
                user.getId().toString(),
                user.getUsername(),
                List.of("ROLE_" + user.getRole()),
                user.getAuthVersion());
    }

    void grant(long transport, Account user) {
        jdbc.update("INSERT INTO transport_assignment VALUES (?,?)", transport, user.getId());
    }

    ResultActions getAs(String path, String token) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    void hidden(String path, String token) throws Exception {
        getAs(path, token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("message").value("Record not found"));
    }

    <T> T as(String token, Supplier<T> operation) {
        var previous = SecurityContextHolder.getContext();
        var previousRequest = RequestContextHolder.getRequestAttributes();
        var converter =
                new org.springframework.security.oauth2.server.resource.authentication
                        .JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("roles");
        converter.setAuthorityPrefix("");
        var jwt = decoder.decode(token);
        var authentication = new JwtAuthenticationToken(jwt, converter.convert(jwt));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        var request = new MockHttpServletRequest();
        request.setAttribute(
                RequestContextFilterCustom.REQUEST_CONTEXT_ATTRIBUTE,
                new RequestContext(
                        authentication.getName(), ActorType.USER, "service-test", "service-test"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            return operation.get();
        } finally {
            SecurityContextHolder.setContext(previous);
            RequestContextHolder.setRequestAttributes(previousRequest);
        }
    }

    @Test
    void usersReadOnlyAssignedParentsAndChildren() throws Exception {
        getAs("/api/transports/10/jobs", aliceToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(11));
        getAs("/api/jobs/11", aliceToken).andExpect(status().isOk());
        getAs("/api/jobs/11/containers", aliceToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12));
        getAs("/api/containers/12", aliceToken).andExpect(status().isOk());
        for (String path :
                List.of(
                        "/api/transports/20/jobs",
                        "/api/jobs/21",
                        "/api/jobs/21/containers",
                        "/api/containers/22",
                        "/api/transports/30/jobs",
                        "/api/jobs/31",
                        "/api/containers/32",
                        "/api/jobs/999")) hidden(path, aliceToken);
        hidden("/api/jobs/11", bobToken);
        getAs("/api/jobs/21", bobToken).andExpect(status().isOk());
        assertThat(as(aliceToken, () -> transports.findAll()).stream().map(Transport::getId))
                .containsExactly(10L);
        assertThat(as(bobToken, () -> transports.findAll()).stream().map(Transport::getId))
                .containsExactly(20L);
        assertThat(as(aliceToken, () -> transports.findById(10L)).getId()).isEqualTo(10L);
        assertThatThrownBy(() -> as(aliceToken, () -> transports.findById(20L)))
                .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void emptyAssignedCollectionsReturn200AndSharedAssignmentsDoNotDuplicateLists()
            throws Exception {
        jdbc.update("DELETE FROM container WHERE job_id=11");
        getAs("/api/jobs/11/containers", aliceToken)
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        jdbc.update("DELETE FROM job WHERE id=11");
        getAs("/api/transports/10/jobs", aliceToken)
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        grant(10, bob);
        assertThat(as(aliceToken, () -> transports.findAll()).stream().map(Transport::getId))
                .containsExactly(10L);
        assertThat(as(bobToken, () -> transports.findAll()).stream().map(Transport::getId))
                .containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void revokedAssignmentIsEffectiveWithTheSameTokenOnTheNextRequest() throws Exception {
        getAs("/api/jobs/11", aliceToken).andExpect(status().isOk());
        jdbc.update(
                "DELETE FROM transport_assignment WHERE transport_id=10 AND user_id=?",
                alice.getId());
        for (String path :
                List.of(
                        "/api/transports/10/jobs",
                        "/api/jobs/11",
                        "/api/jobs/11/containers",
                        "/api/containers/12")) hidden(path, aliceToken);
        assertThat(as(aliceToken, () -> transports.findAll())).isEmpty();
    }

    @Test
    void deletedTransportHidesDescendantsEvenFromAdmin() throws Exception {
        jdbc.update("UPDATE transport SET deleted_at=now() WHERE id=10");
        for (String token : List.of(aliceToken, adminToken)) {
            for (String path :
                    List.of(
                            "/api/transports/10/jobs",
                            "/api/jobs/11",
                            "/api/jobs/11/containers",
                            "/api/containers/12")) hidden(path, token);
            assertThatThrownBy(() -> as(token, () -> transports.findById(10L)))
                    .isInstanceOf(RecordNotFoundException.class);
        }
        assertThat(as(adminToken, () -> transports.findAll()).stream().map(Transport::getId))
                .containsExactlyInAnyOrder(20L, 30L);
        mvc.perform(
                        post("/api/transports/10/jobs")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content("{\"reference\":\"hidden-parent\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/jobs/11/containers")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content("{\"containerNumber\":\"hidden-parent\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedJobsHideContainersAndDeletedChildrenAreExcludedFromLists() throws Exception {
        jdbc.update("UPDATE job SET deleted_at=now() WHERE id=11");
        for (String token : List.of(aliceToken, adminToken)) {
            hidden("/api/jobs/11", token);
            hidden("/api/jobs/11/containers", token);
            hidden("/api/containers/12", token);
            getAs("/api/transports/10/jobs", token)
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }
        jdbc.update("UPDATE container SET deleted_at=now() WHERE id=22");
        hidden("/api/containers/22", bobToken);
        getAs("/api/jobs/21/containers", bobToken)
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    Map<String, List<Map<String, Object>>> snapshot() {
        var result = new HashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("transport", "job", "container", "transport_assignment"))
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        return result;
    }

    @Test
    void everyHttpWriteIncludingHierarchyIsForbiddenAndLeavesDatabaseUnchanged() throws Exception {
        var before = snapshot();
        String hierarchyBody =
                "{\"reference\":\"DENIED\",\"jobs\":[{\"reference\":\"J\",\"containers\":[{\"containerNumber\":\"C\"}]}]}";
        var requests =
                List.of(
                        post("/api/transports").content("{\"reference\":\"DENIED\"}"),
                        post("/api/transports/with-jobs").content(hierarchyBody),
                        post("/api/transports/10/jobs").content("{\"reference\":\"DENIED\"}"),
                        post("/api/jobs/11/containers").content("{\"containerNumber\":\"DENIED\"}"),
                        patch("/api/transports/10/status").content("{\"status\":\"IN_PROGRESS\"}"),
                        patch("/api/jobs/11/status").content("{\"status\":\"IN_PROGRESS\"}"),
                        patch("/api/containers/12/status").content("{\"status\":\"LOADED\"}"),
                        put("/api/transports/10").content("{}"),
                        delete("/api/jobs/11"));
        for (var request : requests)
            mvc.perform(
                            request.header("Authorization", "Bearer " + aliceToken)
                                    .header("X-User-Id", admin.getId())
                                    .contentType("application/json"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("error").value("Forbidden"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void serviceBoundariesCannotBeBypassedForCreateUpdateOrDelete() {
        var before = snapshot();
        List<Runnable> writes =
                List.of(
                        () -> transports.create("DENIED"),
                        () -> jobs.create(10L, "DENIED"),
                        () -> containers.create(11L, "DENIED"),
                        () -> transports.changeStatus(10L, TransportStatus.IN_PROGRESS),
                        () -> jobs.changeStatus(11L, JobStatus.READY),
                        () -> containers.changeStatus(12L, ContainerStatus.LOADED),
                        () -> transports.delete(10L),
                        () -> jobs.delete(11L),
                        () -> containers.delete(12L),
                        () ->
                                hierarchy.create(
                                        "DENIED",
                                        List.of(
                                                new TransportHierarchyService.NewJob(
                                                        "DENIED", List.of("DENIED")))));
        for (Runnable write : writes)
            assertThatThrownBy(
                            () ->
                                    as(
                                            aliceToken,
                                            () -> {
                                                write.run();
                                                return null;
                                            }))
                    .isInstanceOf(AccessDeniedException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void adminsCanReadEveryActiveRecordAndUseExistingCreateAndStatusEndpoints() throws Exception {
        for (String path :
                List.of(
                        "/api/jobs/11",
                        "/api/jobs/21",
                        "/api/jobs/31",
                        "/api/containers/12",
                        "/api/transports/30/jobs"))
            getAs(path, adminToken).andExpect(status().isOk());
        assertThat(as(adminToken, () -> transports.findAll())).hasSize(3);
        String body =
                "{\"reference\":\"ADMIN-HIERARCHY\",\"jobs\":[{\"reference\":\"NEW-JOB\",\"containers\":[{\"containerNumber\":\"NEW-CONTAINER\"}]}]}";
        mvc.perform(
                        post("/api/transports/with-jobs")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/transports")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content("{\"reference\":\"ADMIN-TRANSPORT\"}"))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/transports/10/jobs")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content("{\"reference\":\"ADMIN-JOB\"}"))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/jobs/11/containers")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content("{\"containerNumber\":\"ADMIN-CONTAINER\"}"))
                .andExpect(status().isCreated());
        for (String path : List.of("/api/transports/10/status", "/api/jobs/11/status"))
            mvc.perform(
                            patch(path)
                                    .header("Authorization", "Bearer " + adminToken)
                                    .contentType("application/json")
                                    .content("{\"status\":\"IN_PROGRESS\"}"))
                    .andExpect(status().isOk());
        mvc.perform(
                        patch("/api/containers/12/status")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content("{\"status\":\"LOADED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminServiceDeletesRemainSupportedAndPreserveAuditActor() {
        as(
                adminToken,
                () -> {
                    containers.delete(12L);
                    jobs.delete(11L);
                    transports.delete(10L);
                    return null;
                });
        for (String table : List.of("transport", "job", "container")) {
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM "
                                            + table
                                            + " WHERE deleted_at IS NOT NULL",
                                    Long.class))
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT updated_by FROM "
                                            + table
                                            + " WHERE deleted_at IS NOT NULL",
                                    String.class))
                    .isEqualTo(admin.getId().toString());
        }
    }

    @Test
    void missingInvalidAndRolelessAuthenticationCannotReadOrWrite() throws Exception {
        for (String path :
                List.of("/api/jobs/11", "/api/transports/10/jobs", "/api/containers/12")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            getAs(path, "invalid").andExpect(status().isUnauthorized());
        }
        String roleless =
                tokens.generateToken(
                        alice.getId().toString(), "alice", List.of(), alice.getAuthVersion());
        getAs("/api/jobs/11", roleless).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/transports")
                                .contentType("application/json")
                                .content("{\"reference\":\"DENIED\"}"))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> as(roleless, () -> transports.findAll()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(snapshot().get("transport")).hasSize(3);
    }
}
