package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;

class LoginRateLimitFilterTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    @Test
    void boundsMemorySeparatesPeersAndResetsWindow() {
        var clock = new MutableClock();
        var limit = new LoginRateLimitFilter(2, 2, Duration.ofMinutes(1), clock);
        assertThat(limit.allow("one")).isTrue();
        assertThat(limit.allow("one")).isTrue();
        assertThat(limit.allow("one")).isFalse();
        assertThat(limit.allow("two")).isTrue();
        assertThat(limit.allow("three")).isFalse();
        clock.now = clock.now.plusSeconds(60);
        assertThat(limit.allow("three")).isTrue();
        assertThat(limit.allow("one")).isTrue();
    }

    @Test
    void limitsOnlyLoginAndIgnoresSpoofedForwardedHeader() throws Exception {
        var limit = new LoginRateLimitFilter(1, 10, Duration.ofMinutes(1), Clock.systemUTC());
        var calls = new AtomicInteger();
        for (int attempt = 0; attempt < 2; attempt++) {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "spoof-" + attempt);
            var response = new MockHttpServletResponse();
            limit.doFilter(request, response, (req, res) -> calls.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(attempt == 0 ? 200 : 429);
            if (attempt == 1) assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        }
        limit.doFilter(
                new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(),
                (req, res) -> calls.incrementAndGet());
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void registrationRoutesShareTheirOwnLimitAndDoNotDisableLoginThrottling() throws Exception {
        var login = new LoginRateLimitFilter(1, 10, Duration.ofMinutes(1), Clock.systemUTC());
        var registration =
                new LoginRateLimitFilter(
                        1,
                        10,
                        Duration.ofMinutes(1),
                        Clock.systemUTC(),
                        java.util.Set.of(
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification"));
        var paths =
                java.util.List.of(
                        "/api/auth/register",
                        "/api/auth/resend-verification",
                        "/api/auth/login",
                        "/api/auth/login");
        var expected = java.util.List.of(200, 429, 200, 429);
        for (int i = 0; i < paths.size(); i++) {
            var request = new MockHttpServletRequest("POST", paths.get(i));
            request.setRemoteAddr("127.0.0.1");
            var response = new MockHttpServletResponse();
            login.doFilter(
                    request, response, (req, res) -> registration.doFilter(req, res, (a, b) -> {}));
            assertThat(response.getStatus()).isEqualTo(expected.get(i));
        }
    }
}
