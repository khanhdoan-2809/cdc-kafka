-- Controlled backfill for an existing account with no email; never marks it verified.
-- psql --set=user_id=<account-uuid> --set=email=user@example.com --file=scripts/sql/backfill-account-email.sql
\set ON_ERROR_STOP on
BEGIN;
UPDATE auth_user SET email=lower(trim(:'email')), email_verified=FALSE
WHERE id=:'user_id'::uuid AND email IS NULL
RETURNING id, username, email, email_verified;
COMMIT;
-- A successful backfill returns exactly one row. Then request resend-verification and use the emailed code.
-- Zero rows means the account does not exist or already has an email. Investigate; do not overwrite it.
