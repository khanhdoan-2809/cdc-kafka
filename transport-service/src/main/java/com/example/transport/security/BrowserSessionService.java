package com.example.transport.security;

import com.example.transport.domain.auth.LoginResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class BrowserSessionService {
    private final AccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final JwtService jwt;
    private final SecureRandom random = new SecureRandom();

    public BrowserSessionService(
            AccountRepository accounts,
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            Clock clock,
            JwtService jwt) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.clock = clock;
        this.jwt = jwt;
    }

    /** Internal result only: refresh secrets must never be serialized or logged. */
    public record Credentials(
            @com.fasterxml.jackson.annotation.JsonIgnore String refreshToken,
            Instant expiresAt,
            LoginResponse access) {
        @Override
        public String toString() {
            return "Credentials[REDACTED]";
        }
    }

    private record Stored(
            UUID user, UUID family, long version, String role, Instant expires, boolean consumed) {}

    public Credentials login(AccountPrincipal principal) {
        return transactions.execute(
                status -> {
                    var account =
                            accounts.lockById(UUID.fromString(principal.getAccountId()))
                                    .orElseThrow(BrowserSessionService::invalid);
                    String role = "ROLE_" + account.getRole();
                    if (!valid(account, principal.getAuthVersion(), account.getRole())
                            || principal.getAuthorities().size() != 1
                            || !principal
                                    .getAuthorities()
                                    .iterator()
                                    .next()
                                    .getAuthority()
                                    .equals(role)) throw invalid();
                    // Never substitute a newer version for the credential-authentication snapshot.
                    Instant expires =
                            clock.instant()
                                    .truncatedTo(ChronoUnit.SECONDS)
                                    .plus(Duration.ofDays(7));
                    return issue(account, UUID.randomUUID(), expires);
                });
    }

    public Credentials refresh(String token) {
        Credentials result = use(token, false);
        // Replay revocation must COMMIT before returning 401; throwing inside the transaction
        // would roll back the version increment and leave the stolen session usable.
        if (result == null) throw invalid();
        return result;
    }

    public void logout(String token) {
        if (use(token, true) == null) throw invalid();
    }

    private Credentials use(String token, boolean logout) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String hash = RegistrationService.digest(token);
        return transactions.execute(
                status -> {
                    var first = find(hash);
                    if (first == null) return null;
                    var account = accounts.lockById(first.user()).orElse(null);
                    // Reload only after the account lock: a competing refresh may have consumed it.
                    var stored = find(hash);
                    if (account == null
                            || stored == null
                            || !stored.expires().isAfter(clock.instant())
                            || account.getAuthVersion() != stored.version()) return null;
                    if (stored.consumed()) {
                        account.invalidateSessions();
                        return null;
                    }
                    if (!valid(account, stored.version(), stored.role())) return null;
                    if (logout) {
                        account.invalidateSessions();
                        return new Credentials(null, stored.expires(), null);
                    }
                    jdbc.update(
                            "update browser_refresh_token set consumed_at=? where token_hash=?",
                            Timestamp.from(clock.instant()),
                            hash);
                    return issue(account, stored.family(), stored.expires());
                });
    }

    private boolean valid(Account account, long version, String role) {
        return account.isEnabled()
                && account.isEmailVerified()
                && account.getEmail() != null
                && !account.getEmail().isBlank()
                && Set.of("ADMIN", "USER").contains(account.getRole())
                && account.getRole().equals(role)
                && account.getAuthVersion() == version;
    }

    private Stored find(String hash) {
        var found =
                jdbc.query(
                        "select user_id,family_id,auth_version,role,expires_at,consumed_at from"
                                + " browser_refresh_token where token_hash=?",
                        (rs, row) ->
                                new Stored(
                                        rs.getObject(1, UUID.class),
                                        rs.getObject(2, UUID.class),
                                        rs.getLong(3),
                                        rs.getString(4),
                                        rs.getTimestamp(5).toInstant(),
                                        rs.getTimestamp(6) != null),
                        hash);
        return found.isEmpty() ? null : found.getFirst();
    }

    private Credentials issue(Account account, UUID family, Instant expires) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var access =
                jwt.generateTokenUntil(
                        account.getId().toString(),
                        account.getUsername(),
                        List.of("ROLE_" + account.getRole()),
                        account.getAuthVersion(),
                        expires);
        jdbc.update(
                "insert into"
                    + " browser_refresh_token(token_hash,user_id,family_id,auth_version,role,issued_at,expires_at)"
                    + " values (?,?,?,?,?,?,?)",
                RegistrationService.digest(token),
                account.getId(),
                family,
                account.getAuthVersion(),
                account.getRole(),
                Timestamp.from(clock.instant()),
                Timestamp.from(expires));
        return new Credentials(
                token,
                expires,
                new LoginResponse(
                        access.getTokenValue(),
                        "Bearer",
                        Duration.between(access.getIssuedAt(), access.getExpiresAt()).toSeconds()));
    }

    public void cleanupExpired() {
        jdbc.update(
                "delete from browser_refresh_token where expires_at<=?",
                Timestamp.from(clock.instant()));
    }

    private static BadCredentialsException invalid() {
        return new BadCredentialsException("Invalid browser session");
    }
}
