#!/usr/bin/env bash
# Runs on the PROD EC2 host through SSM as root. Never print database secrets.
set -euo pipefail
umask 077

AWS_REGION="${1:?AWS region required}"
ECR_REGISTRY="${2:?ECR registry required}"
RELEASE_TAG="${3:?Release tag required}"

[[ "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid release tag' >&2; exit 1; }

cd /opt/cdc-kafka

if [[ ! -f /var/lib/cdc-kafka/bootstrap-ready ]]; then
  echo 'EC2 bootstrap incomplete' >&2
  exit 1
fi

# AL2023 has network access through the host's public subnet.
if ! command -v jq >/dev/null; then dnf install -y -q jq >/dev/null; fi

# Fail early if Phase 5 database initialization has not created secret versions.
read_secret() {
  local secret_id="$1"
  aws secretsmanager get-secret-value --region "$AWS_REGION" \
    --secret-id "$secret_id" --query SecretString --output text
}

transport_secret="$(read_secret 'cdc-kafka/prod/database/transport-app')"
audit_secret="$(read_secret 'cdc-kafka/prod/database/audit-app')"

for secret in "$transport_secret" "$audit_secret"; do
  jq -e '.username | type == "string"' >/dev/null <<<"$secret"
  jq -e '.password | type == "string"' >/dev/null <<<"$secret"
done

# Read only resource endpoints; do not store infrastructure output in GitHub variables.
RDS_HOST="$(aws rds describe-db-instances --region "$AWS_REGION" \
  --db-instance-identifier cdc-kafka-prod-postgres \
  --query 'DBInstances[0].Endpoint.Address' --output text)"

MSK_ARN="$(aws kafka list-clusters-v2 --region "$AWS_REGION" \
  --cluster-name-filter cdc-kafka-prod \
  --query 'ClusterInfoList[?ClusterName==`cdc-kafka-prod`].ClusterArn | [0]' --output text)"

[[ -n "$MSK_ARN" && "$MSK_ARN" != "None" ]] || { echo 'Production MSK cluster not found' >&2; exit 1; }

MSK_BROKERS="$(aws kafka get-bootstrap-brokers --region "$AWS_REGION" \
  --cluster-arn "$MSK_ARN" \
  --query BootstrapBrokerStringSaslIam --output text)"

[[ -n "$RDS_HOST" && "$RDS_HOST" != "None" && -n "$MSK_BROKERS" && "$MSK_BROKERS" != "None" ]] || {
  echo 'Production RDS/MSK endpoint unavailable' >&2
  exit 1
}

TRANSPORT_IMAGE="${ECR_REGISTRY}/cdc-kafka-transport-service:${RELEASE_TAG}"
AUDIT_IMAGE="${ECR_REGISTRY}/cdc-kafka-audit-consumer:${RELEASE_TAG}"

# Build a private env file, then atomically replace the previous release config
# only after both images have been pulled successfully.
ENV_TEMP="$(mktemp /opt/cdc-kafka/.env.prod.XXXXXXXX)"
trap 'rm -f "$ENV_TEMP"' EXIT

{
  printf 'TRANSPORT_IMAGE=%s\n' "$TRANSPORT_IMAGE"
  printf 'AUDIT_IMAGE=%s\n' "$AUDIT_IMAGE"
  printf 'RDS_HOST=%s\n' "$RDS_HOST"
  printf 'MSK_BROKERS=%s\n' "$MSK_BROKERS"
  jq -r '"TRANSPORT_DB_USERNAME=" + .username, "TRANSPORT_DB_PASSWORD=" + .password' <<<"$transport_secret"
  jq -r '"AUDIT_DB_USERNAME=" + .username, "AUDIT_DB_PASSWORD=" + .password' <<<"$audit_secret"
} > "$ENV_TEMP"

unset transport_secret audit_secret
chmod 600 "$ENV_TEMP"

aws ecr get-login-password --region "$AWS_REGION" | docker login \
  --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null

# Pull before updating .env.prod, keeping the current release until the images exist.
docker compose --env-file "$ENV_TEMP" -f compose.prod.yml pull
mv -f "$ENV_TEMP" .env.prod
trap - EXIT

docker compose --env-file .env.prod -f compose.prod.yml up -d --no-build --remove-orphans

# Allow the applications to start and finish their Flyway checks.
for attempt in $(seq 1 60); do
  if curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health/readiness >/dev/null 2>&1 \
      && curl -fsS --max-time 5 http://127.0.0.1:8081/actuator/health/readiness >/dev/null 2>&1; then
    echo "Release $RELEASE_TAG healthy on localhost"
    docker compose --env-file .env.prod -f compose.prod.yml ps
    exit 0
  fi
  sleep 5
done

echo 'Release did not become healthy. No automatic rollback; inspect logs with SSM.' >&2
docker compose --env-file .env.prod -f compose.prod.yml ps >&2
exit 1
