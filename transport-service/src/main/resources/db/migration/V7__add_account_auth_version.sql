ALTER TABLE auth_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE auth_user ADD CONSTRAINT ck_auth_version_nonnegative CHECK (auth_version >= 0);
-- Existing tokens do not contain auth_version and must be replaced by logging in again.
-- Account state remains outside the business CDC publication.
