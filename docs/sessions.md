# Redis sessions and account-wide logout

RS256 access tokens expire after 15 minutes by default. PostgreSQL stores accounts and password hashes; Redis is the authoritative allowlist of active login sessions. There are no refresh tokens. Login again after expiry, logout, or session-store loss.

## HTTP contract

| Endpoint | Authentication | Result |
| --- | --- | --- |
| POST /api/auth/login | Username and password | accessToken, tokenType, expiresIn |
| GET /api/auth/me | Active USER or ADMIN bearer session | id, username, email, role only |
| POST /api/auth/logout | Active USER or ADMIN bearer session | 204 after revoking every session for that account |

Logout has no required body. Body IDs and X-User-Id cannot select another account. A revoked token, including a repeated logout, returns 401. Existing response shapes, transport assignment checks, Basic monitoring credentials, and audit actor attribution are preserved.

JWTs require canonical UUID sub and jti claims, issuer, audience, issued-at and expiry, and an RS256 signature. They no longer contain auth_version. Session metadata binds account ID, username, email, role, jti and exact expiry. Tokens are signed with whole-second timestamps so the encoded JWT and stored metadata agree. Passwords and raw bearer tokens never enter Redis.

## Request validation and storage

After cryptographic and required-claim validation, RedisSessionStore performs one script execution to validate the session and obtain its public account snapshot. No account SQL lookup runs on normal bearer validation or /me. A cold Redis script cache may require the driver's EVALSHA-to-EVAL fallback; warmed validation is one Redis round trip. Business requests still query PostgreSQL for business data and transport assignments.

Each account uses two keys sharing the same Redis hash tag:

- auth:sessions:{account-uuid}:data: hash of jti to JSON metadata.
- auth:sessions:{account-uuid}:expiry: sorted set of jti to expiry milliseconds.

Lua atomically creates sessions, removes expired entries during creation, and expires both keys at the latest session's expiry plus Spring Security's existing 60-second timestamp tolerance. Validation requires the corresponding index entry and matching expiry, and never renews TTL. Missing, expired, corrupt, or mismatched sessions return 401. Redis communication, authentication, timeout, or capacity failures return generic 503, without database or JWT-only fallback.

## Concurrency and controlled account changes

Login locks the PostgreSQL account row by username BEFORE validating credentials, and holds that lock through Redis session creation. Logout locks the same row by ID and atomically verifies its caller's session before deleting both Redis keys. Concurrent logouts have one winner; a stale logout cannot revoke a subsequent login. A login already checking credentials completes before a waiting logout, and that logout revokes its newly issued session too. Sessions are returned only after Redis creation and successful transaction completion. Requests already in flight are not canceled.

AccountSecurityService uses the same row lock and performs Redis revocation before changing PostgreSQL state. Redis failure aborts the database change. Database failure after revocation can sign the account out, but cannot restore revoked sessions. A timeout or error must not be treated as confirmed success; retry after recovery.

Use the operator-only CLI for existing accounts. It runs a separate non-web JVM inside the deployed container, using its configured DB/Redis credentials. It exposes no administration HTTP routes:

```bash
bash scripts/account-admin.sh revoke <account-uuid>
bash scripts/account-admin.sh set-role <account-uuid> ADMIN
bash scripts/account-admin.sh set-enabled <account-uuid> false
bash scripts/account-admin.sh delete <account-uuid>
```

For production, prefix commands with COMPOSE_FILE=compose.prod.yml COMPOSE_ENV_FILE=.env.prod. For DEV's explicit env file use COMPOSE_ENV_FILE=.env.dev (and the appropriate COMPOSE_FILE override if needed). Commands return nonzero on failure and do not print passwords or tokens.

Direct SQL changes no longer update active session snapshots or revoke tokens immediately. Role changes, disable/enable and deletion must use the coordinated service/CLI. Do not combine a CLI revoke followed by arbitrary SQL while traffic is live: a login could interleave. Unsupported password/email/security edits require maintenance with application instances stopped, database edits completed, and Redis sessions cleared before traffic resumes. Future password/email management must acquire the account lock and revoke Redis sessions before committing. Email verification of a pending account remains in PostgreSQL; pending accounts cannot have login sessions.

## Redis configuration and recovery

Local/DEV and production Compose run a private redis:7.4.12-alpine container without a host port. It requires authentication, runs as the redis user, has a 256 MiB container limit, a 128 MiB data limit, and noeviction. Snapshotting, append-only persistence and replication are disabled. Existing session data is never reconstructed from PostgreSQL or bearer tokens.

Run bash scripts/init-dev-auth.sh for local/DEV. It creates secrets/redis-password if absent and preserves it on later runs. Production requires a separately provisioned /etc/cdc-kafka/redis-password, readable by the deployment identity and protected on the host. The deploy script checks that this file exists. REDIS_PASSWORD_FILE overrides the Compose source path. Both application and Redis mount the same password file as /run/secrets/redis-password; Spring reads it through configtree. Avoid conflicting REDIS_PASSWORD environment overrides.

Standalone application runs accept REDIS_HOST, REDIS_PORT and REDIS_PASSWORD. REDIS_CONNECT_TIMEOUT and REDIS_COMMAND_TIMEOUT default to one second. Redis and database health contribute to readiness; liveness only reflects application availability. Health endpoints remain public and do not expose secret details.

A Redis restart clears every login session, so all users must log in again. An application restart retains sessions while Redis stays running. Never restore old session snapshots or attach a replica: stale allowlist data could restore revoked sessions. This initial deployment accepts Redis downtime as authentication downtime. Multiple application instances must share this Redis instance and the primary PostgreSQL database.

Metrics auth.sessions.duration and auth.sessions.failures record store latency and operational failures with operation/outcome tags only, without token or account labels. Prometheus renders these as auth_sessions_duration_seconds_* and auth_sessions_failures_total. Watch readiness, failure rate, and capacity; normal token rejection is not counted as an infrastructure failure.

## Cutover and rollback

Pause authenticated/business traffic and drain all old instances before enabling this release. Provision the Redis password, start Redis, deploy the new application, and verify readiness and the session smoke test before resuming traffic. All existing tokens require login again because no corresponding Redis session exists. Never mix nodes using database versions with nodes using Redis sessions.

Flyway V7 and the auth_user.auth_version column remain unchanged for schema-history compatibility. Runtime code no longer maps, reads, or increments that column. Do not edit old migration checksums or remove the column during this release. Credentials and session metadata remain outside business CDC.

Rollback also requires maintenance and drained instances. Clear Redis sessions, invalidate any tokens the target application might otherwise accept (rotate signing keys when returning to the old database-version application), and require fresh login before resuming traffic. Merely reverting the application does not provide a safe session migration.

## Verification

Install Java 21 and a real redis-server on PATH, or set REDIS_TEST_EXECUTABLE to its absolute path. Tests launch isolated password-protected Redis instances on random loopback ports with persistence disabled; they never connect to a developer's shared Redis. Native PostgreSQL 17 is supplied by the existing embedded-postgres test dependency. Docker is not required for Maven tests. Linux CI installs Redis before running ./mvnw -B clean verify.

Coverage includes logout across devices, account isolation, concurrent login/logout and simultaneous logouts, controlled changes and transaction failures, missing/malformed JWT claims, corrupt/mismatched/expired Redis state, cleanup and timestamp tolerance, real connection failures, real Redis restart, full application restart, and the operator configuration. /me checks assert one store validation and zero JDBC/repository interactions or Hibernate SQL statements.

An optional warmed lookup comparison against the previous primary-key account query writes target/auth-session-benchmark.json (p50/p95 microseconds and account SQL counts):

```bash
RUN_AUTH_BENCHMARK=true ./mvnw -B -pl transport-service -Dtest=SessionLifecycleIntegrationTest test
```

This local lookup comparison excludes HTTP and signature verification and does not establish production throughput. Run the existing load suite against comparable deployments to assess production latency and database load.

On a deployed isolated stack, provide a dedicated verified test account and run:

```bash
SMOKE_USERNAME=session-test SMOKE_PASSWORD='<test-password>' bash scripts/smoke-redis-sessions.sh
```

Set SMOKE_RESTART_SERVICES=true only on an isolated smoke stack to also restart the application (token survives) and Redis (token fails). This signs everyone out of that stack. COMPOSE_FILE and COMPOSE_ENV_FILE select the same deployment as for the operator CLI; SMOKE_BASE_URL overrides the default loopback API URL. The script revokes the test account's sessions and never prints its tokens.
