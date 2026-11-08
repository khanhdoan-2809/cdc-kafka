package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

class SessionSecurityTest {
    static JwtEncoder encoder;
    static JwtDecoder decoder;
    static JwtService tokens;

    @BeforeAll
    static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var config = new JwtKeyConfig();
        encoder = config.jwtEncoder(pair);
        decoder = config.jwtDecoder(pair, "https://auth.test", "transport-test");
        tokens = new JwtService(encoder, "https://auth.test", "transport-test", Duration.ofMinutes(15));
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    Account account() {
        var account = new Account("user", "unused-hash", "USER", "user@test.invalid");
        account.verifyEmail();
        return account;
    }

    @Test
    void signedTokenAndRedisMetadataHaveIdenticalWholeSecondExpiry() {
        var account = account();
        var issued = tokens.generateToken(account.getId().toString(), "user", List.of("ROLE_USER"));
        var decoded = decoder.decode(issued.getTokenValue());
        assertThat(decoded.getExpiresAt()).isEqualTo(issued.getExpiresAt());
        assertThat(decoded.getExpiresAt().getNano()).isZero();
        assertThat(decoded.getClaims()).doesNotContainKey("auth_version");
        assertThat(UUID.fromString(decoded.getId())).isNotNull();
    }

    @Test
    void missingAndMalformedJtiAreRejectedByTheCryptographicDecoder() {
        for (String id : Arrays.asList(null, "", "bad", "1-1-1-1-1")) {
            var claims = JwtClaimsSet.builder().subject(UUID.randomUUID().toString())
                    .issuer("https://auth.test").audience(List.of("transport-test"))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900));
            if (id != null) claims.id(id);
            var signed = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()));
            assertThatThrownBy(() -> decoder.decode(signed.getTokenValue())).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void liveFilterFailsClosedForMissingSessionsAndUnavailableRedis() throws Exception {
        var store = mock(RedisSessionStore.class);
        var account = account();
        var jwt = decoder.decode(tokens.generateToken(account.getId().toString(), "user", List.of("ROLE_USER")).getTokenValue());
        var filter = new LiveAccountFilter(store, new JwtAuthenticationEntryPoint(), new ObjectMapper());
        for (boolean unavailable : List.of(false, true)) {
            reset(store);
            if (unavailable) when(store.validate(jwt)).thenThrow(new DataAccessResourceFailureException("private detail"));
            else when(store.validate(jwt)).thenReturn(Optional.empty());
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
            var request = new MockHttpServletRequest("GET", "/api/auth/me");
            var response = new MockHttpServletResponse();
            var reached = new AtomicBoolean();
            filter.doFilter(request, response, (req, res) -> reached.set(true));
            assertThat(response.getStatus()).isEqualTo(unavailable ? 503 : 401);
            assertThat(response.getContentAsString()).doesNotContain("private detail");
            assertThat(reached).isFalse();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(store).validate(jwt);
            verifyNoMoreInteractions(store);
        }
    }

    @Test
    void liveFilterPopulatesPublicContextFromTheSession() throws Exception {
        var store = mock(RedisSessionStore.class);
        var account = account();
        var jwt = decoder.decode(tokens.generateToken(account.getId().toString(), "user", List.of("ROLE_USER")).getTokenValue());
        var state = new AccountSessionState(account.getId(), "user", account.getEmail(), "USER", UUID.fromString(jwt.getId()), jwt.getExpiresAt());
        when(store.validate(jwt)).thenReturn(Optional.of(state));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        var request = new MockHttpServletRequest("GET", "/api/auth/me");
        var response = new MockHttpServletResponse();
        new LiveAccountFilter(store, new JwtAuthenticationEntryPoint(), new ObjectMapper())
                .doFilter(request, response, (req, res) -> assertThat(req.getAttribute(LiveAccountFilter.ACCOUNT_ATTRIBUTE)).isSameAs(state));
        assertThat(response.getStatus()).isEqualTo(200);
        verify(store).validate(jwt);
        verifyNoMoreInteractions(store);
    }

    @Test
    void loginLocksBeforeCheckingCredentialsAndCreatesSessionBeforeReturning() {
        var account = account();
        var accounts = mock(AccountRepository.class);
        var manager = mock(AuthenticationManager.class);
        var store = mock(RedisSessionStore.class);
        when(accounts.lockByUsername("user")).thenReturn(Optional.of(account));
        var principal = new AccountPrincipal(account);
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        var service = new LoginService(accounts, manager, tokens, store);
        var result = service.login("user", "test-password");
        var order = inOrder(accounts, manager, store);
        order.verify(accounts).lockByUsername("user");
        order.verify(manager).authenticate(any());
        order.verify(store).create(argThat(state -> state.id().equals(account.getId())
                && state.accepts(decoder.decode(result.getAccessToken()))));
        reset(store);
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        assertThatThrownBy(() -> service.login("user", "wrong")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(store);
    }

    @Test
    void securityMutationRevokesBeforeChangingStateAndAbortsWhenRedisFails() {
        var account = account();
        var accounts = mock(AccountRepository.class);
        var store = mock(RedisSessionStore.class);
        when(accounts.lockById(account.getId())).thenReturn(Optional.of(account));
        var service = new AccountSecurityService(accounts, store);
        doThrow(new DataAccessResourceFailureException("unavailable")).when(store).revokeAll(account.getId());
        assertThatThrownBy(() -> service.apply(account.getId(), "set-enabled", "false")).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(account.isEnabled()).isTrue();
        doAnswer(invocation -> { assertThat(account.isEnabled()).isTrue(); return null; }).when(store).revokeAll(account.getId());
        service.apply(account.getId(), "set-enabled", "false");
        assertThat(account.isEnabled()).isFalse();
        clearInvocations(store);
        assertThatThrownBy(() -> service.apply(account.getId(), "set-role", "SUPERUSER")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(account.getId(), "set-enabled", "yes")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(account.getId(), "unknown", "")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }
}
