#!/usr/bin/env bash

set -euo pipefail

AWS_REGION="${1:?AWS region is required}"
ECR_REGISTRY="${2:?ECR registry is required}"
COMMIT_SHA="${3:?Commit SHA is required}"

cd /opt/cdc-kafka

if [[ ! -f .env.dev ]]; then
  echo "Creating DEV environment"

  umask 077

  cat > .env.dev <<ENV
TRANSPORT_DB_NAME=transport
TRANSPORT_DB_USERNAME=transport
TRANSPORT_DB_PASSWORD=$(openssl rand -hex 24)

AUDIT_DB_NAME=audit
AUDIT_DB_USERNAME=audit
AUDIT_DB_PASSWORD=$(openssl rand -hex 24)

GRAFANA_ADMIN_USERNAME=admin
GRAFANA_ADMIN_PASSWORD=$(openssl rand -hex 24)
ENV
fi

export TRANSPORT_IMAGE="${ECR_REGISTRY}/cdc-kafka-transport-service:${COMMIT_SHA}"
export AUDIT_IMAGE="${ECR_REGISTRY}/cdc-kafka-audit-consumer:${COMMIT_SHA}"

TOKEN="$(
  curl -fsS \
    -X PUT \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 60" \
    http://169.254.169.254/latest/api/token
)"

export DEV_PUBLIC_HOST="$(
  curl -fsS \
    -H "X-aws-ec2-metadata-token: ${TOKEN}" \
    http://169.254.169.254/latest/meta-data/public-ipv4
)"

aws ecr get-login-password \
  --region "${AWS_REGION}" \
| docker login \
  --username AWS \
  --password-stdin "${ECR_REGISTRY}"

COMPOSE=(
  docker compose
  --env-file .env.dev
  -f compose.yml
  -f compose.dev.yml
)

"${COMPOSE[@]}" pull

"${COMPOSE[@]}" up \
  -d \
  --no-build \
  --remove-orphans

"${COMPOSE[@]}" ps