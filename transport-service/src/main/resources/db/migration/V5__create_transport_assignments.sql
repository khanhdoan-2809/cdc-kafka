CREATE TABLE transport_assignment (
    transport_id BIGINT NOT NULL REFERENCES transport(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE,
    PRIMARY KEY (transport_id, user_id)
);
CREATE INDEX idx_transport_assignment_user ON transport_assignment(user_id);
-- Existing transports intentionally remain unassigned. Only explicit grants allow USER reads.
-- Authorization metadata is not part of the business CDC publication or Debezium allowlist.
