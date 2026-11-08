package com.example.transport.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Repository
public class RedisSessionStore {
    public static final long CLOCK_SKEW_SECONDS = 60;
    private static final DefaultRedisScript<Long> CREATE = script("create-session", Long.class);
    private static final DefaultRedisScript<String> VALIDATE = script("validate-session", String.class);
    private static final DefaultRedisScript<Long> LOGOUT = script("logout-sessions", Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final MeterRegistry metrics;

    public RedisSessionStore(StringRedisTemplate redis, ObjectMapper json, MeterRegistry metrics) {
        this.redis = redis;
        this.json = json;
        this.metrics = metrics;
    }

    private static <T> DefaultRedisScript<T> script(String name, Class<T> type) {
        var script = new DefaultRedisScript<T>();
        script.setLocation(new ClassPathResource("redis/" + name + ".lua"));
        script.setResultType(type);
        return script;
    }

    static List<String> keys(UUID accountId) {
        String prefix = "auth:sessions:{" + accountId + "}:";
        return List.of(prefix + "data", prefix + "expiry");
    }

    private static String expiry(Instant expiresAt) {
        return Long.toString(expiresAt.plusSeconds(CLOCK_SKEW_SECONDS).toEpochMilli());
    }

    public void create(AccountSessionState session) {
        measured("create", () -> {
            Long result = redis.execute(CREATE, keys(session.id()), session.sessionId().toString(),
                    encode(session), expiry(session.expiresAt()));
            if (!Long.valueOf(1).equals(result))
                throw new DataAccessResourceFailureException("Session creation unavailable");
            return null;
        });
    }

    public Optional<AccountSessionState> validate(Jwt jwt) {
        return measured("validate", () -> {
            String value = redis.execute(VALIDATE, keys(UUID.fromString(jwt.getSubject())),
                    jwt.getId(), expiry(jwt.getExpiresAt()));
            if (value == null) return Optional.empty();
            try {
                var state = json.readValue(value, AccountSessionState.class);
                return state != null && state.accepts(jwt) ? Optional.of(state) : Optional.empty();
            } catch (JsonProcessingException | IllegalArgumentException | NullPointerException invalid) {
                // Corrupt or mismatched metadata is never an authenticated identity.
                return Optional.empty();
            }
        });
    }

    public void revokeAll(UUID accountId) {
        measured("revoke", () -> redis.delete(keys(accountId)));
    }

    public boolean revokeAllIfActive(AccountSessionState session) {
        return measured("logout", () -> Long.valueOf(1).equals(redis.execute(LOGOUT,
                keys(session.id()), session.sessionId().toString(), encode(session),
                expiry(session.expiresAt()))));
    }

    private String encode(AccountSessionState session) {
        try {
            return json.writeValueAsString(session);
        } catch (JsonProcessingException invalid) {
            throw new DataAccessResourceFailureException("Session metadata unavailable");
        }
    }

    private <T> T measured(String operation, Supplier<T> action) {
        var sample = Timer.start(metrics);
        String outcome = "success";
        try {
            return action.get();
        } catch (RuntimeException failure) {
            outcome = "failure";
            metrics.counter("auth.sessions.failures", "operation", operation).increment();
            throw failure;
        } finally {
            sample.stop(metrics.timer("auth.sessions.duration", "operation", operation,
                    "outcome", outcome));
        }
    }
}
