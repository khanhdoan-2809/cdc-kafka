ALTER TABLE auth_user ADD COLUMN email VARCHAR(254);
ALTER TABLE auth_user ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE auth_user ADD CONSTRAINT uk_auth_user_email UNIQUE (email);
ALTER TABLE auth_user ADD CONSTRAINT ck_verified_email_present CHECK (NOT email_verified OR email IS NOT NULL);
ALTER TABLE auth_user ADD CONSTRAINT ck_auth_user_email_normalized CHECK (email IS NULL OR email = lower(trim(email)));
CREATE TABLE email_verification_token (
    user_id UUID PRIMARY KEY REFERENCES auth_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > issued_at)
);
-- Legacy accounts intentionally remain unverified with no email. Backfill explicitly and verify.
-- Credentials and verification tokens stay outside the business CDC publication and allowlist.
