-- DEV-only assignment seed for an existing USER account and active transport.
-- Configure the database connection with standard psql/PG* settings; supply both variables.
-- psql --set=transport_id=123 --set=user_id=<account-uuid> --file=scripts/sql/grant-transport-access.sql
\set ON_ERROR_STOP on
BEGIN;
SELECT EXISTS (
    SELECT 1 FROM transport t CROSS JOIN auth_user u
    WHERE t.id = :'transport_id'::bigint AND t.deleted_at IS NULL
      AND u.id = :'user_id'::uuid AND u.role = 'USER' AND u.enabled
) AS assignment_target_exists \gset
\if :assignment_target_exists
INSERT INTO transport_assignment (transport_id, user_id)
VALUES (:'transport_id'::bigint, :'user_id'::uuid)
ON CONFLICT (transport_id, user_id) DO NOTHING;
COMMIT;
\else
ROLLBACK;
\echo 'An active transport and enabled USER account are required.'
\quit 1
\endif
