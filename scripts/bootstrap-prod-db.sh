#!/usr/bin/env bash
# One-time, rerunnable RDS bootstrap. Executed on the PROD EC2 host via SSM.
# Database credentials are read locally from Secrets Manager, not from GitHub.
set -euo pipefail
umask 077

AWS_REGION="${1:?AWS region is required}"

command -v aws >/dev/null
command -v docker >/dev/null
command -v jq >/dev/null || dnf install -y -q jq >/dev/null

secret() {
  aws secretsmanager get-secret-value --region "$AWS_REGION" \
    --secret-id "$1" --query SecretString --output text
}

master_arn="$(aws rds describe-db-instances --region "$AWS_REGION" \
  --db-instance-identifier cdc-kafka-prod-postgres \
  --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)"
rds_host="$(aws rds describe-db-instances --region "$AWS_REGION" \
  --db-instance-identifier cdc-kafka-prod-postgres \
  --query 'DBInstances[0].Endpoint.Address' --output text)"

[[ -n "$master_arn" && "$master_arn" != None && -n "$rds_host" && "$rds_host" != None ]] || {
  echo 'RDS endpoint or administrator secret not available' >&2
  exit 1
}

master="$(secret "$master_arn")"
transport="$(secret cdc-kafka/prod/database/transport-app)"
audit="$(secret cdc-kafka/prod/database/audit-app)"
debezium="$(secret cdc-kafka/prod/database/debezium)"

# No passwords in SSM commands, arguments, SQL source files, or application logs.
# The temporary Docker env file is root-only and removed on exit.
env_file="$(mktemp)"
trap 'rm -f "$env_file"' EXIT
{
  printf 'PGHOST=%s\nPGPORT=5432\nPGDATABASE=postgres\nPGSSLMODE=require\n' "$rds_host"
  printf 'PGUSER=%s\n' "$(jq -er '.username' <<< "$master")"
  printf 'PGPASSWORD=%s\n' "$(jq -er '.password' <<< "$master")"
  printf 'TRANSPORT_DB_PASSWORD=%s\n' "$(jq -er '.password' <<< "$transport")"
  printf 'AUDIT_DB_PASSWORD=%s\n' "$(jq -er '.password' <<< "$audit")"
  printf 'DEBEZIUM_DB_PASSWORD=%s\n' "$(jq -er '.password' <<< "$debezium")"
} > "$env_file"
unset master transport audit debezium
chmod 600 "$env_file"

# RDS creates the transport database. We only need to create audit, roles,
# and permissions. Transport Flyway migrations create the publication and tables.
docker run --rm -i --network host --env-file "$env_file" postgres:17-alpine \
  psql -X -v ON_ERROR_STOP=1 -q <<'SQL'
-- Logical decoding must already be active for the Debezium connector.
DO $check$
BEGIN
  IF current_setting('wal_level') <> 'logical' THEN
    RAISE EXCEPTION 'RDS wal_level must be logical; reboot RDS after applying the replication parameter group';
  END IF;
END
$check$;

\getenv transport_password TRANSPORT_DB_PASSWORD
\getenv audit_password AUDIT_DB_PASSWORD
\getenv debezium_password DEBEZIUM_DB_PASSWORD

SELECT format('CREATE ROLE transport_app LOGIN PASSWORD %L', :'transport_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'transport_app') \gexec
SELECT format('ALTER ROLE transport_app LOGIN PASSWORD %L', :'transport_password') \gexec

SELECT format('CREATE ROLE audit_app LOGIN PASSWORD %L', :'audit_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_app') \gexec
SELECT format('ALTER ROLE audit_app LOGIN PASSWORD %L', :'audit_password') \gexec

SELECT format('CREATE ROLE debezium LOGIN PASSWORD %L', :'debezium_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'debezium') \gexec
SELECT format('ALTER ROLE debezium LOGIN PASSWORD %L', :'debezium_password') \gexec

GRANT rds_replication TO debezium;

SELECT 'CREATE DATABASE audit'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'audit') \gexec

GRANT CONNECT, CREATE ON DATABASE transport TO transport_app;
GRANT CONNECT ON DATABASE transport TO debezium;
GRANT CONNECT, CREATE ON DATABASE audit TO audit_app;

\connect transport
GRANT USAGE, CREATE ON SCHEMA public TO transport_app;
GRANT USAGE ON SCHEMA public TO debezium;

\connect audit
GRANT USAGE, CREATE ON SCHEMA public TO audit_app;
SQL

echo 'Production RDS bootstrap completed (roles, audit database, replication privileges).'
