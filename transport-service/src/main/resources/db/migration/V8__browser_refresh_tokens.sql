CREATE TABLE browser_refresh_token (
    token_hash VARCHAR(64) PRIMARY KEY CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    user_id UUID NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    auth_version BIGINT NOT NULL CHECK (auth_version >= 0),
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CHECK (expires_at > issued_at),
    CHECK (consumed_at IS NULL OR consumed_at >= issued_at)
);
CREATE INDEX ix_browser_refresh_expiry ON browser_refresh_token(expires_at);
CREATE INDEX ix_browser_refresh_user ON browser_refresh_token(user_id);
-- Refresh credentials stay outside the business CDC publication and allowlist.
