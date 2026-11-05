# Authorization: roles and transport assignments

The first authorization task uses the existing ADMIN and USER roles. It does not add public registration, refresh tokens or account-management APIs.

| Operation | USER | ADMIN |
| --- | --- | --- |
| Read business records | Assigned active transports and their active jobs/containers | All active records |
| Create or change status | Denied | Allowed |
| Soft-delete through existing service operations | Denied | Allowed |

Only the existing business endpoints are exposed. No new transport-read or delete HTTP endpoint is added. JobController delegates authorization to service boundaries; its original comment examples are retained. HTTP role checks also reject business writes before controllers run. Login and health remain public; monitoring retains its separate credentials. Unmatched routes are denied.

## Record scope

Flyway V5 creates transport_assignment with a composite primary key, foreign keys to transport/auth_user, and a user_id lookup index. Multiple users can share a transport. Jobs and containers inherit assignments through their parent transport. SQL queries apply scope before results leave the database; no in-memory list filtering is used. Read methods join active parents, so a deleted transport/job hides descendants even from ADMIN. Missing and inaccessible individual records and parent collections return the same 404 response. Empty collections belonging to an accessible parent return 200 with an empty array.

Assignments are queried on each read and are not embedded in JWTs or cached. Revoking one takes effect with the existing token on the next request. No existing records are assigned automatically. An authenticated USER without assignments has no business-record visibility. Read scope uses the verified account UUID, never X-User-Id. ADMIN bypasses assignments, but not deletion filters.

## DEV setup

Create a USER account through the controlled database provisioning process described in the main README. This task deliberately provides no public signup or assignment-management API.

For an existing enabled USER and active transport, use the DEV seed script with your configured psql connection:

```bash
psql --set=transport_id=123 --set=user_id=<account-uuid> --file=scripts/sql/grant-transport-access.sql
```

Replace the placeholders with actual IDs; quote the UUID as required by your shell. The script validates both targets and is idempotent. Do not grant access to every existing user or transport. To revoke a grant, use a controlled, parameterized deletion of the exact transport_id/user_id pair. Managed assignment APIs are scheduled for task 4.

Assignment metadata, account records and password hashes remain outside the business CDC publication and connector table allowlist. There is no change to existing endpoint URLs, successful JSON response shapes or business audit actor attribution. A denied write must not change business records.

## Tests and rollout

`./mvnw -B clean verify` runs the existing authentication tests and the new authorization and migration tests. Authorization tests run against real PostgreSQL 17, validate the JPA mapping against Flyway-created tables, and use real signed bearer tokens. The embedded PostgreSQL dependency downloads native test binaries; Docker is not required. Run tests as an ordinary user on Linux. Existing authentication tests continue using H2.

Test coverage includes two users, shared/unassigned records, direct/nested reads, filtered lists, revoked grants, deleted ancestors, all existing ADMIN write endpoints, direct service access, and unchanged database snapshots after denied writes. Migration tests cover fresh creation, V4 upgrades, constraints and CDC exclusions.

Deploy V5 together with the service change. Back up the database and seed only explicitly approved assignments before USER reads are needed. Keep public registration disabled. If application rollback is necessary, disable USER API access or take the API offline first: the old application does not enforce assignments. The additive V5 table can remain during rollback; do not roll back to broader access while serving USER requests.
