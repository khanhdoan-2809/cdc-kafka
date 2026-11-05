# CDC Kafka transport platform

Transport service writes PostgreSQL business records; Debezium publishes them to Kafka and audit-consumer persists audit records.

## Authentication setup

Requires Java 21, Docker Compose, Bash and OpenSSL. For local/DEV:

```bash
bash scripts/init-dev-auth.sh
docker compose up -d --build
```

The initialization script generates a fresh 3072-bit RSA pair and random initial admin/monitoring passwords under ignored `secrets/`. It is idempotent and never overwrites existing keys or accounts. Read `secrets/auth.env` locally to obtain the initial admin credentials; do not commit or share it. Development deployment invokes this script automatically. Windows users can run it in Git Bash with OpenSSL available.

`POST /api/auth/login` accepts JSON `username` and `password`, without `X-User-Id`. The response contains `accessToken`, `tokenType: Bearer`, and `expiresIn` in seconds. Send `Authorization: Bearer <accessToken>` to the API. Do not send passwords or tokens over public HTTP; production requires HTTPS ingress. Avoid shell tracing when working with credentials.

Accounts are persisted in `auth_user` by Flyway V4. Passwords use BCrypt cost 12. The bootstrap username/password create an ADMIN only when the account table is empty; they never reset an existing account. There is no registration or user administration API in this change. Provision subsequent accounts through a controlled database administration process using generated UUIDs, BCrypt hashes and role `ADMIN` or `USER`; never store plaintext passwords. Username matching is case-sensitive. Bootstrap usernames accept 1-100 ASCII letters, digits, `.`, `_`, `@`, `-`; bootstrap passwords require at least 12 characters and at most 72 UTF-8 bytes. Remove bootstrap credentials from configuration after first provisioning and supply explicit smoke credentials thereafter.

JWT subjects are immutable account UUIDs. Username is a separate claim. Existing `ROLE_ADMIN` / `ROLE_USER` authorities and the current job-controller restriction are preserved. This change does not implement the planned authorization policy.

## Runtime configuration

| Variable | Purpose |
| --- | --- |
| `JWT_PRIVATE_KEY` | Required external `file:` URI to a PKCS#8 RSA private key |
| `JWT_PUBLIC_KEY` | Required external `file:` URI to the matching X.509 public key |
| `JWT_ISSUER` | Required issuer, validated on every bearer token |
| `JWT_AUDIENCE` | Required audience, validated on every bearer token |
| `JWT_TTL` | ISO-8601 duration; default `PT15M`, allowed 1 second to 1 hour |
| `AUTH_BOOTSTRAP_USERNAME`, `AUTH_BOOTSTRAP_PASSWORD` | Optional one-time initial ADMIN provisioning |
| `MONITORING_PASSWORD` | Required dedicated monitoring password, 12 characters to 72 UTF-8 bytes |
| `AUTH_LOGIN_MAX_ATTEMPTS` | Login attempts per peer IP per window; default 10 |
| `AUTH_LOGIN_WINDOW` | ISO-8601 window duration; default `PT1M` |
| `AUTH_LOGIN_MAX_PEERS` | Maximum tracked IPs; default 10000; capacity exhaustion fails closed |

Startup fails for absent, malformed, mismatched or weak RSA keys. Only RS256 bearer tokens are accepted, with mandatory expiry, issued-at, UUID subject, issuer and audience checks. Spring Security applies its default timestamp clock-skew tolerance (60 seconds). Tokens expire independently of account state: disabling an account prevents new logins, while previously issued tokens remain usable until expiration. There are no refresh tokens or logout revocation; log in again after expiry.

Login throttling is bounded, in-memory and per process, keyed by the actual peer IP. It counts successful and failed attempts, returns 429 with Retry-After, and does not trust client-supplied forwarded headers. Behind a proxy, peers may share a limit; apply per-client throttling at a trusted ingress. Multiple replicas need a shared limiter or ingress enforcement. Restarting a process resets its counters.

## Trusted audit identity

`X-User-Id` is ignored. After bearer authentication, the verified subject populates request context, logging MDC and JPA `created_by`/`updated_by`. Request/correlation IDs remain trace metadata. The account table is excluded from the business CDC publication and Debezium table allowlist, so credentials are not deliberately published to Kafka. Existing historical audit rows retain their old identities.

## Monitoring and deployment

Health, liveness and readiness are public GET endpoints. Transport `/actuator/prometheus`, `/actuator/metrics` and `/actuator/info` use separate HTTP Basic credentials: username `monitoring` and `MONITORING_PASSWORD`. These credentials cannot log in to the business API. Local Prometheus reads the matching password from `secrets/monitoring-password`; keep that file synchronized when rotating the password. Use TLS or a private network for Basic authentication. Audit-consumer's existing management access is unchanged.

For production, provision `/etc/cdc-kafka/auth.env` containing `JWT_ISSUER`, `JWT_AUDIENCE`, `MONITORING_PASSWORD` and optional bootstrap credentials. Put keys in `/etc/cdc-kafka/jwt/private-key.pem` and `public.pem`. `compose.prod.yml` mounts them read-only. Override host paths via `AUTH_ENV_FILE` and `JWT_KEYS_DIR` when invoking Compose. Restrict secret files to the deployment/container identity and use your secret manager to provision them. The existing production deploy script uses these default host paths; secrets must be present before deploying. Do not use the DEV initialization script in production.

The old signing key was publicly committed. Replace it in every deployment that used it. This implementation deliberately accepts only the new key; old tokens require re-login. Rotation: generate a new pair outside the repository, replace the configured pair together, restart all transport instances consistently, and verify new login plus rejection of old tokens. Removal from this checkout does not remove the historical key from Git history; it must be treated as compromised.

The DEV smoke test authenticates with `SMOKE_USERNAME`/`SMOKE_PASSWORD`, falling back to the generated bootstrap credentials. It verifies that the CDC actor matches the token subject. For load tests, set `K6_USERNAME` and `K6_PASSWORD` to an ADMIN account (the current job policy requires it), then run `bash scripts/run-load-test.sh baseline`. Built-in profiles finish before the default token expires; longer scenarios need token renewal.

## Verification

```bash
./mvnw -B clean verify
```

Authentication integration tests use fresh temporary RSA keys and an H2 PostgreSQL-mode database. They cover real login/token validation, persisted audit identities, disabled accounts, invalid input, monitoring isolation and the existing admin restriction. Unit tests cover bootstrap and login rate limits. H2 tests do not run PostgreSQL Flyway migrations or the full Kafka/Debezium pipeline; run the authenticated DEV smoke test against the Compose environment before deployment.
