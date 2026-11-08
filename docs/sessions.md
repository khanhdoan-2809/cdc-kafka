# Session invalidation and logout (task 3A)

Access tokens still expire after 15 minutes by default. API login remains access-token-only, without a token blacklist. Optional [browser refresh sessions](browser-sessions.md) add hashed refresh-token rows and share this account-wide invalidation counter. Password recovery/change remain PR 3B.

## API

| Endpoint | Authentication | Result |
| --- | --- | --- |
| GET /api/auth/me | Current valid USER or ADMIN bearer token | 200 with id, username, email, role only |
| POST /api/auth/logout | Current valid USER or ADMIN bearer token | 204 after invalidating every token for the caller |

No body is required for logout. Request body IDs and X-User-Id cannot select another account. USER needs no transport assignment to inspect their own profile or log out. A revoked token, including a second logout using that token, returns 401. Login response fields remain accessToken, tokenType and expiresIn; the JWT now contains a required integer auth_version.

## Validation and concurrency

Every bearer-authenticated request first validates the RSA signature, issuer, audience, timestamps, UUID subject and nonnegative integer auth_version. It then performs one fresh primary-key PostgreSQL query for current account state, excluding password hashes. A missing account, disabled/unverified email, changed role, mismatched version, malformed/missing version claim or expired token returns generic 401. The check happens before audit-identity population and business code. Invalid cryptographic/required claims do not query account state. Unknown/non-JWT authentication cannot bypass the bearer chain.

Live state is request-local and is not cached across requests. /me uses that validated state and never serializes the password hash, version or internal status flags. Health requests without a bearer token and the separate Basic monitoring chain keep their existing behavior.

The account version is captured in AccountPrincipal during credential authentication and passed unchanged to JWT signing. A concurrent logout cannot be bypassed by rereading a newer version after old credentials were checked. Logout locks the account, rechecks current status/role/version, increments auth_version and commits before returning 204. Concurrent logouts serialize; a loser with a stale snapshot returns 401. Other accounts are unaffected. New login after logout produces a usable token at the current version.

Checks apply when a request enters; work already in flight is not canceled. A database failure at lookup or during logout returns 503, exposes no connection details and never falls back to JWT-only authorization. A failed logout must not be treated as confirmed account-wide revocation; retry after recovery. Locally discarding a token alone does not revoke other sessions.

## Account changes and rollout

Flyway V7 adds auth_user.auth_version BIGINT NOT NULL DEFAULT 0 with a nonnegative constraint. It changes no account IDs, passwords, roles, email-verification state or assignments, and does not publish credentials to business CDC. The entity increments its version when enabled status changes or email verification is activated. Future password/role/admin operations must lock the same account row and increment this counter within their transaction. Controlled raw SQL changes to security fields must also increment auth_version; never reset or decrement the counter. A changed role/disabled/unverified account is independently checked even if an operator omits a version bump.

Tokens issued before this release lack auth_version and are rejected immediately by the new application; everyone must log in again. Clients should discard stale local tokens and omit Authorization when logging in again. Apply V7 with the application release and drain all old instances before restoring authenticated traffic. Do not serve a mixture of old nodes that ignore versions and new nodes that enforce them. No signing-key rotation is required for the versioned-token cutover. Keep public registration disabled until remaining roadmap tasks are ready.

If rolling back, put authenticated/business traffic into maintenance first: the previous application does not enforce revocation. The additive column can remain, but serving the old application would weaken session invalidation. Do not decrease stored versions to revive old tokens.

## Verification

`./mvnw -B clean verify` covers existing auth/authorization/registration tests plus PostgreSQL V6–V7 migration, current-account response isolation, all-session logout, re-login, role/status changes, malformed/missing/stale versions, database/transaction failures and concurrent login/logout. A latch-based test pauses after credential validation and logs out before allowing token issuance; that raced token must be rejected.

An additional native PostgreSQL + loopback sandbox SMTP smoke test runs register -> receive the real SMTP message -> verify -> login -> /me -> logout -> rejected old token. It uses the production mail adapter, not the integration mailbox fake, and sends no mail outside the test server. This does not replace a check against deployed DEV Mailpit/SMTP, production TLS delivery, Docker image builds or the full Kafka/Debezium smoke flow. Run the deployed DEV email smoke before deployment; Docker may be required there, not for the automated native tests.
