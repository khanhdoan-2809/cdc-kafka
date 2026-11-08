# Public registration and email verification (task 2)

Public registration is disabled by default. This task adds backend APIs only; recovery, refresh tokens, session revocation and account administration remain later tasks. Existing comments and role/assignment enforcement are preserved.

## API

All three endpoints accept JSON and are anonymously reachable, including when signup is disabled. Registration itself returns 404 while AUTH_REGISTRATION_ENABLED=false. Verification/resend remain available so existing accounts and bootstrap administrators can finish verification.

| Endpoint | Request | Result |
| --- | --- | --- |
| POST /api/auth/register | username, email, password | 202 generic message, no token/account details |
| POST /api/auth/resend-verification | email | Same generic 202 for eligible, unknown, disabled, verified and cooldown cases |
| POST /api/auth/verify-email | token | 200 on success; generic 400 for invalid, expired or reused tokens |

Registration/resend responses say: `If eligible, verification instructions will be sent.` Duplicate registration never changes an existing password, email, role or verification code. Concurrent identifier collisions still return the generic response. Unknown request fields are ignored: clients cannot grant themselves ADMIN, mark email verified or assign records.

Usernames stay case-sensitive and contain 1-100 ASCII letters, digits, `.`, `_`, `@`, or `-`. Email is trimmed, lowercased and restricted to ASCII addresses up to 254 characters. Passwords require at least 15 Unicode code points and at most 72 UTF-8 bytes; BCrypt cost 12 remains in use. Passwords are never trimmed or silently truncated.

A new account is enabled but unverified, always USER, with no assignments. Login retains username/password and returns the same generic 401 for unverified, disabled or invalid credentials. Verifying an email does not create assignments or issue a JWT; the user must log in separately.

## Verification and abuse protection

Codes contain 32 random bytes encoded as URL-safe Base64 (43 characters). Only SHA-256 hashes are stored. Codes expire after 24 hours and are consumed atomically with email verification. A per-account row lock coordinates verify/resend operations: at most one concurrent verification succeeds. Resend is eligible after 60 seconds and replaces the previous code. There is only one stored code per account, so expired codes cannot accumulate per account.

Login retains its own peer-IP limit. Register, verify and resend share a separate bounded peer-IP bucket, configured by AUTH_REGISTRATION_MAX_ATTEMPTS (default 10) and the existing login window/max-peers settings. Forwarded headers are not trusted. Shared ingress enforcement remains a release-readiness task; process-local counters reset on restart.

SMTP runs after commit on a bounded executor (one worker, at most two, queue capacity 100). Delivery failure, process restart or queue exhaustion leaves a pending account that can use resend after the cooldown. There is no durable mail outbox or automatic delivery retry in this task. Responses do not wait for SMTP. Application delivery warnings omit message contents and credentials; password/code DTO string representations are redacted.

## DEV setup

`bash scripts/init-dev-auth.sh` generates new DEV files with AUTH_BOOTSTRAP_EMAIL=admin@cdc-kafka.local. Existing secret files/accounts are never silently rewritten. If reusing an older secrets/auth.env with a fresh database, add AUTH_BOOTSTRAP_EMAIL explicitly before startup. Local Compose sends mail to Mailpit on its internal port 1025; the inbox UI is bound only to http://127.0.0.1:8025. Signup remains disabled unless you explicitly set AUTH_REGISTRATION_ENABLED=true in secrets/auth.env for DEV testing.

On a fresh database, bootstrap credentials create an unverified ADMIN and queue its verification email. Open Mailpit, copy the code, and POST it as `{ "token": "..." }` to /api/auth/verify-email. Then login and the existing smoke/load clients work as before. If delivery failed, fix SMTP and POST `{ "email": "admin@cdc-kafka.local" }` to /api/auth/resend-verification after 60 seconds. No initial admin is automatically trusted just because its credentials came from configuration.

## Existing-account upgrade

Flyway V6 adds nullable unique email, email_verified=false, and email_verification_token. It preserves existing UUIDs, passwords, roles and assignments, and grants no verification exemptions. Existing accounts cannot obtain new login tokens until their operator-confirmed email is backfilled and verified, including ADMIN.

1. Keep public signup disabled and plan the login interruption before rollout. Back up the database and configure working mail delivery.
2. Deploy V6 and the application. Existing credentials alone no longer allow a fresh login.
3. Using a controlled database connection, run `scripts/sql/backfill-account-email.sql` with explicit user_id and email psql variables. It only changes an account whose email is NULL, returns the changed identity and leaves email_verified=false. Validate the target and recipient out of band; never assign an email based only on an unauthenticated request. Zero returned rows require investigation.
4. Request resend-verification, have the recipient verify the emailed code, and confirm login plus assigned-record access. Verify at least the bootstrap/admin account before relying on admin operations.
5. Remove bootstrap credentials from runtime configuration after setup. Do not expose signup publicly until the remaining roadmap tasks are complete.

Task 3A now requires auth_version and checks current account state on every bearer request. Tokens issued before task 3A are rejected and require re-login; see the coordinated rollout in docs/sessions.md. SMTP failure does not mark anyone verified. Account and token tables remain outside the business CDC publication and Debezium allowlist.

## Production configuration

compose.prod.yml activates the transport prod profile. Supply SMTP_HOST, SMTP_PORT (default 587), SMTP_USERNAME, SMTP_PASSWORD, AUTH_VERIFICATION_FROM and, for a fresh bootstrap only, AUTH_BOOTSTRAP_EMAIL through the protected auth.env/secret provisioning process. Production mail requires authenticated STARTTLS with certificate identity checks. Never copy the DEV no-TLS settings into production. Mailpit is not part of compose.prod.yml.

Bare local Java runs must configure SMTP_HOST/SMTP_PORT and explicitly set SMTP_AUTH=false and SMTP_STARTTLS_REQUIRED=false only when using a trusted local mail catcher. DEV Compose applies these settings automatically. Mailpit messages contain live verification codes: keep its UI private and never use it for production accounts.

## Verification

Maven tests cover PostgreSQL V5–V6 upgrade and fresh schema validation, generic duplicates and concurrent registration, token hashing/expiry/reuse, atomic concurrent verification, cooldown/rotation, disabled accounts, SMTP failure recovery, privileged-input isolation, password byte limits, the default-off flag, and unchanged assignment rules. SMTP adapter tests inspect generated messages; Docker/Mailpit and real production SMTP still need a DEV delivery smoke test before deployment.
