package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

class RedisSessionStoreIntegrationTest {
    static TestRedis server;
    static LettuceConnectionFactory connections;
    static StringRedisTemplate redis;
    static ObjectMapper json;
    static SimpleMeterRegistry metrics;
    static RedisSessionStore store;
    UUID account;

    @BeforeAll
    static void connect() {
        server = new TestRedis();
        var config = new RedisStandaloneConfiguration("127.0.0.1", server.port);
        config.setPassword(server.password);
        connections = new LettuceConnectionFactory(config);
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
        json = new ObjectMapper().findAndRegisterModules();
        metrics = new SimpleMeterRegistry();
        store = new RedisSessionStore(redis, json, metrics);
    }

    @AfterAll
    static void disconnect() {
        if (connections != null) connections.destroy();
        if (server != null) server.close();
        if (metrics != null) metrics.close();
    }

    @BeforeEach
    void identity() { account = UUID.randomUUID(); }

    AccountSessionState session(Instant expiry) {
        return new AccountSessionState(account, "user", "user@test.invalid", "USER", UUID.randomUUID(), expiry);
    }

    static Jwt jwt(AccountSessionState session) {
        return Jwt.withTokenValue("test-token-never-stored").header("alg", "RS256")
                .subject(session.id().toString()).claim("jti", session.sessionId().toString())
                .issuedAt(session.expiresAt().minusSeconds(900)).expiresAt(session.expiresAt())
                .claim("username", session.username()).claim("roles", List.of("ROLE_" + session.role())).build();
    }

    static Instant now() { return Instant.now().truncatedTo(ChronoUnit.SECONDS); }

    @Test
    void activeSessionsValidateAndConditionalLogoutRevokesOnlyTheirAccount() {
        var one = session(now().plusSeconds(900));
        var two = session(now().plusSeconds(900));
        var other = new AccountSessionState(UUID.randomUUID(), "other", "other@test.invalid", "USER", UUID.randomUUID(), one.expiresAt());
        for (var state : List.of(one, two, other)) store.create(state);
        assertThat(store.validate(jwt(one))).contains(one);
        assertThat(store.validate(jwt(two))).contains(two);
        assertThat(store.revokeAllIfActive(session(one.expiresAt()))).isFalse();
        assertThat(store.revokeAllIfActive(one)).isTrue();
        assertThat(store.revokeAllIfActive(two)).isFalse();
        assertThat(store.validate(jwt(one))).isEmpty();
        assertThat(store.validate(jwt(two))).isEmpty();
        assertThat(store.validate(jwt(other))).contains(other);
    }

    @Test
    void metadataMustMatchEveryIdentityRoleAndExpiryField() throws Exception {
        var state = session(now().plusSeconds(900));
        store.create(state);
        var key = RedisSessionStore.keys(account).getFirst();
        var id = state.sessionId().toString();
        String valid = json.writeValueAsString(state);
        for (String field : List.of("id", "sessionId", "username", "email", "role", "expiresAt")) {
            var changed = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(valid);
            changed.putNull(field);
            redis.opsForHash().put(key, id, changed.toString());
            assertThat(store.validate(jwt(state))).as(field).isEmpty();
        }
        for (String bad : List.of("not-json", "null", "{}", valid.replace("USER", "ADMIN"), valid.replace("user\"", "changed\""))) {
            redis.opsForHash().put(key, id, bad);
            assertThat(store.validate(jwt(state))).isEmpty();
        }
        redis.opsForHash().put(key, id, valid);
        var differentExpiry = new AccountSessionState(account, state.username(), state.email(), state.role(), state.sessionId(), state.expiresAt().plusSeconds(1));
        assertThat(store.validate(jwt(differentExpiry))).isEmpty();
        redis.delete(RedisSessionStore.keys(account).get(1));
        assertThat(store.validate(jwt(state))).isEmpty();
    }

    @Test
    void expiryCleanupIncludesClockSkewAndDoesNotSlideOrShortenOtherSessions() throws Exception {
        var longer = session(now().plusSeconds(900));
        store.create(longer);
        var keys = RedisSessionStore.keys(account);
        long ttl = redis.getExpire(keys.getFirst());
        var shorter = session(now().plusSeconds(100));
        store.create(shorter);
        assertThat(redis.getExpire(keys.getFirst())).isBetween(ttl - 2, ttl);
        store.validate(jwt(shorter));
        assertThat(redis.getExpire(keys.getFirst())).isBetween(ttl - 2, ttl);
        assertThat(redis.getExpire(keys.get(1))).isBetween(ttl - 2, ttl);
        var grace = session(now().minusSeconds(30));
        store.create(grace);
        assertThat(store.validate(jwt(grace))).contains(grace);
        var expired = session(now().minusSeconds(61));
        assertThatThrownBy(() -> store.create(expired)).isInstanceOf(DataAccessException.class);
        String oldId = expired.sessionId().toString();
        redis.opsForHash().put(keys.getFirst(), oldId, json.writeValueAsString(expired));
        redis.opsForZSet().add(keys.get(1), oldId, expired.expiresAt().plusSeconds(60).toEpochMilli());
        assertThat(store.validate(jwt(expired))).isEmpty();
        store.create(session(now().plusSeconds(60)));
        assertThat(redis.opsForHash().hasKey(keys.getFirst(), oldId)).isFalse();
        assertThat(redis.opsForZSet().score(keys.get(1), oldId)).isNull();
        assertThat(store.validate(jwt(longer))).contains(longer);
    }

    @Test
    void newAdapterRetainsSessionsButRealServerRestartClearsThem() throws Exception {
        var state = session(now().plusSeconds(900));
        store.create(state);
        var replacement = new RedisSessionStore(redis, json, metrics);
        assertThat(replacement.validate(jwt(state))).contains(state);
        server.restart();
        awaitRedis();
        assertThat(store.validate(jwt(state))).isEmpty();
        // A stale logout must not revoke newly created sessions after recovery.
        var fresh = session(now().plusSeconds(900));
        store.create(fresh);
        assertThat(store.revokeAllIfActive(state)).isFalse();
        assertThat(store.validate(jwt(fresh))).contains(fresh);
    }

    static void awaitRedis() throws Exception {
        for (int i = 0; i < 100; i++) {
            try {
                if ("PONG".equals(redis.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping()))) return;
            } catch (DataAccessException restarting) { }
            Thread.sleep(50);
        }
        throw new AssertionError("Redis did not reconnect");
    }

    @Test
    void realConnectionFailureIsReportedAndMeasured() {
        var config = new RedisStandaloneConfiguration("127.0.0.1", server.port);
        config.setPassword("incorrect-password");
        var unavailable = new LettuceConnectionFactory(config);
        unavailable.afterPropertiesSet();
        unavailable.start();
        try {
            var failing = new RedisSessionStore(new StringRedisTemplate(unavailable), json, metrics);
            double before = metrics.counter("auth.sessions.failures", "operation", "validate").count();
            assertThatThrownBy(() -> failing.validate(jwt(session(now().plusSeconds(900)))))
                    .isInstanceOf(DataAccessException.class);
            assertThat(metrics.counter("auth.sessions.failures", "operation", "validate").count()).isEqualTo(before + 1);
            assertThat(metrics.getMeters()).allMatch(meter -> meter.getId().getTags().stream()
                    .allMatch(tag -> Set.of("operation", "outcome").contains(tag.getKey())));
        } finally { unavailable.destroy(); }
    }
}
