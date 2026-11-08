package com.example.transport.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class RegistrationService {
    private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final VerificationEmailSender mail;
    private final boolean enabled;
    private final TaskExecutor emailExecutor;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(
            AccountRepository accounts,
            PasswordEncoder passwords,
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            Clock clock,
            VerificationEmailSender mail,
            @Value("${auth.registration.enabled:false}") boolean enabled,
            @Qualifier("verificationMailExecutor") TaskExecutor emailExecutor) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.clock = clock;
        this.mail = mail;
        this.enabled = enabled;
        this.emailExecutor = emailExecutor;
    }

    public void register(String username, String email, String password) {
        if (!enabled) throw new RegistrationUnavailableException();
        if (password.codePointCount(0, password.length()) < 15
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Invalid registration request");
        // Hash for duplicate requests too; never overwrite an existing account or its token.
        String hash = passwords.encode(password);
        VerificationEmail message;
        try {
            message =
                    transactions.execute(
                            status -> {
                                if (accounts.existsByUsernameOrEmail(username, email)) return null;
                                var account =
                                        accounts.saveAndFlush(
                                                new Account(username, hash, "USER", email));
                                return issue(account);
                            });
        } catch (DataIntegrityViolationException collision) {
            // A competing registration can win either unique identifier. Its account is unchanged.
            if (!accounts.existsByUsernameOrEmail(username, email)) throw collision;
            message = null;
        }
        deliver(message);
    }

    public void resend(String email) {
        VerificationEmail message =
                transactions.execute(
                        status -> {
                            var account = accounts.lockByEmail(email).orElse(null);
                            if (account == null
                                    || account.isEmailVerified()
                                    || !account.isEnabled()) return null;
                            var issued =
                                    jdbc.query(
                                            "select issued_at from email_verification_token where"
                                                + " user_id=?",
                                            (rs, row) -> rs.getTimestamp(1).toInstant(),
                                            account.getId());
                            if (!issued.isEmpty()
                                    && issued.getFirst().plusSeconds(60).isAfter(clock.instant()))
                                return null;
                            return issue(account);
                        });
        deliver(message);
    }

    public void verify(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new InvalidVerificationTokenException();
        String hash = digest(token);
        transactions.executeWithoutResult(
                status -> {
                    var users =
                            jdbc.query(
                                    "select user_id from email_verification_token where"
                                        + " token_hash=?",
                                    (rs, row) -> rs.getObject(1, UUID.class),
                                    hash);
                    if (users.isEmpty()) throw new InvalidVerificationTokenException();
                    var account =
                            accounts.lockById(users.getFirst())
                                    .orElseThrow(InvalidVerificationTokenException::new);
                    if (!account.isEnabled()
                            || account.isEmailVerified()
                            || account.getEmail() == null)
                        throw new InvalidVerificationTokenException();
                    int consumed =
                            jdbc.update(
                                    "delete from email_verification_token where user_id=? and"
                                        + " token_hash=? and expires_at>?",
                                    account.getId(),
                                    hash,
                                    Timestamp.from(clock.instant()));
                    if (consumed != 1) throw new InvalidVerificationTokenException();
                    account.verifyEmail();
                });
    }

    private VerificationEmail issue(Account account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.update(
                "insert into email_verification_token(user_id,token_hash,issued_at,expires_at)"
                    + " values (?,?,?,?) on conflict(user_id) do update set"
                    + " token_hash=excluded.token_hash,issued_at=excluded.issued_at,expires_at=excluded.expires_at",
                account.getId(),
                digest(token),
                Timestamp.from(now),
                Timestamp.from(now.plus(Duration.ofHours(24))));
        return new VerificationEmail(account.getEmail(), token);
    }

    private void deliver(VerificationEmail message) {
        if (message == null) return;
        // Commit account/token state and release locks before contacting SMTP. Resend recovers
        // delivery failures.
        try {
            emailExecutor.execute(
                    () -> {
                        try {
                            mail.send(message);
                        } catch (org.springframework.mail.MailException failure) {
                            LOG.warn(
                                    "Verification email delivery failed; account remains pending"
                                        + " verification");
                        }
                    });
        } catch (TaskRejectedException full) {
            LOG.warn("Verification email queue is full; account remains pending verification");
        }
    }

    static String digest(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
