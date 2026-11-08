# Browser refresh sessions

This optional backend flow provides 15-minute access tokens and rotating refresh cookies with a fixed seven-day family deadline. It is disabled by default. Existing `/api/auth/login`, `/api/auth/logout`, and `/api/auth/me` retain their bearer API contracts. Password recovery and frontend implementation belong to separate PRs.

## Configuration and deployment

Set `AUTH_BROWSER_ENABLED=true`, `JWT_TTL=PT15M`, and `AUTH_BROWSER_ALLOWED_ORIGINS=https://app.example.com` in the transport service environment to enable it. Multiple exact origins are comma-separated, without trailing slashes, paths, wildcards or user information. Startup rejects empty/non-HTTPS origin lists or another access-token lifetime. Choose same-site HTTPS frontend and API hosts, such as `app.example.com` and `api.example.com`. Cross-site deployments are unsupported by the Lax cookie policy.

Both browser and bearer API CORS use these explicit origins when enabled, so the frontend can also call business APIs with Authorization. When disabled, browser routes return 404 and the existing API development CORS configuration remains unchanged. Keep `AUTH_REGISTRATION_ENABLED=false` for public deployments until the release roadmap is complete. Compose already loads the deployment's `auth.env`; no credentials belong in source control.

Browser routes require `request.isSecure()` and reject HTTP with 403 rather than redirecting credential requests. Use direct application TLS or a trusted HTTPS ingress. For TLS termination, configure Spring Boot's native forwarded-header handling and Tomcat's internal/trusted proxy settings to match the actual ingress addresses, and restrict direct backend access to that ingress. The application does not itself trust caller-provided `X-Forwarded-Proto`; forwarding configuration belongs to the deployment. Use the same HTTPS setup in DEV; there is no insecure-cookie switch. Validate the actual ingress/Origin/cookie behavior before enabling this flag.

## API and frontend contract

| Endpoint | Request | Success |
| --- | --- | --- |
| `GET /api/auth/csrf` | HTTPS; include credentials | 200 JSON `{token, headerName}` and a CSRF cookie if absent |
| `POST /api/auth/browser-login` | `{username,password}` plus CSRF header, cookies and allowed Origin | 200 existing `{accessToken,tokenType,expiresIn}` JSON; refresh and rotated CSRF cookies |
| `POST /api/auth/refresh` | Refresh cookie, CSRF header/cookie and allowed Origin; no body or bearer required | 200 access-token JSON; successor refresh cookie |
| `POST /api/auth/browser-logout` | Same browser protections as refresh; no bearer required | 204; account-wide invalidation and cookie deletion |

Use `credentials: "include"` for browser fetches. First get CSRF JSON, then send its token using the returned header name (`X-CSRF-TOKEN`) on every browser POST. Tokens returned by GET are masked by Spring Security's XOR handler; do not read the raw CSRF cookie or synthesize the header from it. The CSRF cookie is also HttpOnly. Login replaces it; obtain fresh CSRF JSON after successful login before refreshing or logging out. Browser logout clears both cookies; obtain CSRF again before another login. These routes use no HTTP session. Responses use `Cache-Control: no-store`.

Keep access tokens in frontend memory and send them in Authorization for `/me` and business APIs. Do not put them in localStorage. Cookies alone cannot authorize a business request. Refresh/logout ignore Authorization, so an expired access token does not block them. The ordinary `/login` sets no cookies and needs no CSRF token; ordinary `/logout` still requires a current bearer token and needs no CSRF token.

Both cookies are host-only `__Host-` cookies, Secure, HttpOnly, Path=/ and SameSite=Lax. Only the refresh cookie persists, with Max-Age limited to the remaining family lifetime. Refresh credentials never appear in JSON; do not log Cookie, Set-Cookie or Authorization at the ingress or in request tracing.

Unsafe browser requests require both a valid CSRF token and exactly one explicitly allowed Origin, including for same-origin clients. Missing/invalid CSRF, missing/null/untrusted/duplicate Origin, and insecure transport return 403 without touching session data. Credential mismatch and invalid/expired/revoked refresh tokens return generic 401. Invalid refresh/logout clears cookies. Database or transaction failure returns generic 503, issues no credentials, and preserves cookies for recovery; do not assume a failed logout completed.

## Rotation, revocation and storage

Flyway V8 adds `browser_refresh_token`, with a SHA-256 token hash, account FK, family UUID, captured auth_version/role, issue/expiry timestamps and consumed_at. Each login creates a fresh 32-byte random Base64url credential and family. Only hashes are persisted; this table is excluded from the business publication and Debezium grants. Account deletion cascades to its refresh rows. Expiry and account indexes support cleanup and FK operations. An hourly scheduled deletion removes only expired rows, on each enabled instance (`AUTH_BROWSER_CLEANUP_DELAY_MS`); unexpired consumed hashes remain available for replay detection.

Login rechecks the credential-authentication snapshot under the account lock; it never upgrades a snapshot to a newer auth_version. Refresh finds the account, locks it, reloads the token, checks account status/verification/role/version/expiry, consumes it and inserts its successor in one transaction. Signing or persistence failure rolls this back before any cookie or access-token JSON is emitted. Rotation copies the original family and deadline. Access expiry is capped at that deadline, with the existing JWT validator's normal 60-second timestamp skew tolerance; expiresIn is reduced near the deadline.

Replaying an unexpired consumed token at the current account version increments auth_version under the same account lock. The transaction commits this revocation before returning 401, invalidating all devices and bearer tokens for that account. Expired or already-version-invalid tokens return 401 without another increment, so stale replays cannot repeatedly invalidate new sessions. Other accounts are unaffected. Existing bearer logout and future transactional password/role changes use this counter too.

Rotation is strict: there is no retry grace. Serialize refresh across tabs/windows, for example using a shared worker or Web Locks plus coordination. Two requests with the same credential cause one rotation followed by replay revocation; even the winner's tokens then fail. Do not automatically retry a refresh after a network timeout with the old credential: if the first committed, that retry revokes all devices. Ask for re-login when rotation outcome is uncertain. A server response can be lost after commit; database atomicity cannot guarantee cookie delivery.

Browser logout uses the refresh credential to increment the same account-wide counter; it works after access expiry. Concurrent logout and refresh serialize: credentials issued before logout commit become invalid on their next use. An in-flight response may arrive after logout, but cannot restore access. Checks do not cancel business requests already in flight. See [RFC 9700 section 4.14.2](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2) and [Spring Security CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html).

Login defaults to 10 attempts per peer/window; refresh and browser logout share 60 per peer/window. The existing bounded per-process limiter returns 429 with Retry-After and counts failed requests too. `AUTH_LOGIN_MAX_ATTEMPTS`, `AUTH_LOGIN_WINDOW`, `AUTH_LOGIN_MAX_PEERS` and `AUTH_BROWSER_MAX_ATTEMPTS` configure these pools. Browser login and API login use separate counters. Shared ingress/distributed limits remain release-readiness work, especially behind proxies or across replicas.

## Rollout and validation

Apply V8 through the new application with the browser flag off first. It is additive: existing account versions and bearer tokens stay valid. Deploy all nodes with this implementation/configuration before enabling browser traffic. Disabling the flag stops all browser routes; existing bearer tokens retain their normal expiry/revocation behavior. The table may remain on rollback, but older applications cannot rotate/clear its cookies. Keep the flag off during rollback and let refresh families expire; do not lower auth_version.

`./mvnw -B clean verify` tests native PostgreSQL V7-to-V8 upgrades, constraints, CDC isolation, real cookies and masked CSRF, allowed-origin/CORS rules, fixed expiry, rotation, committed replay revocation, stale replay, all-session logout, credential/login races, concurrent refresh/logout, failure rollback and unchanged API-client behavior. Existing authorization, verification and sandbox SMTP lifecycle tests remain enabled. These automated checks use MockMvc for HTTPS requests; they do not replace deployed TLS/browser verification. Before deployment run the outstanding DEV register -> receive email -> verify -> login smoke, plus browser login -> refresh -> logout at the actual HTTPS ingress.
