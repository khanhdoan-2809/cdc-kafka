#!/usr/bin/env bash

set -euo pipefail

AWS_REGION="${1:?AWS region is required}"
ECR_REGISTRY="${2:?ECR registry is required}"
COMMIT_SHA="${3:?Commit SHA is required}"

PROJECT_DIR="/opt/cdc-kafka"
ENV_FILE="${PROJECT_DIR}/.env.dev"

cd "${PROJECT_DIR}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Creating DEV credentials"

  umask 077

  cat > "${ENV_FILE}" <<EOF
TRANSPORT_DB_NAME=transport
TRANSPORT_DB_USERNAME=transport
TRANSPORT_DB_PASSWORD=$(openssl rand -hex 24)

AUDIT_DB_NAME=audit
AUDIT_DB_USERNAME=audit
AUDIT_DB_PASSWORD=$(openssl rand -hex 24)

GRAFANA_ADMIN_USERNAME=admin
GRAFANA_ADMIN_PASSWORD=$(openssl rand -hex 24)
EOF
fi

export TRANSPORT_IMAGE="${ECR_REGISTRY}/cdc-kafka-transport-service:${COMMIT_SHA}"
export AUDIT_IMAGE="${ECR_REGISTRY}/cdc-kafka-audit-consumer:${COMMIT_SHA}"

echo "Logging into ECR"

aws ecr get-login-password \
  --region "${AWS_REGION}" \
| docker login \
  --username AWS \
  --password-stdin "${ECR_REGISTRY}"

COMPOSE=(
  docker compose
  -p cdc-kafka
  --env-file "${ENV_FILE}"
  -f compose.yml
  -f compose.dev.yml
)

echo "Validating Compose"

"${COMPOSE[@]}" config --quiet

echo "Pulling images"

"${COMPOSE[@]}" pull

echo "Starting DEV"

"${COMPOSE[@]}" up \
  -d \
  --no-build \
  --remove-orphans

wait_for_health() {
  local service="$1"
  local url="$2"

  echo "Waiting for ${service}"

  for _ in {1..90}; do
    if curl --fail --silent "${url}" >/dev/null; then
      echo "${service} is healthy"
      return
    fi

    sleep 2
  done

  echo "${service} failed health check"

  "${COMPOSE[@]}" logs \
    --tail=200 \
    "${service}"

  exit 1
}

wait_for_health \
  transport-service \
  http://localhost:8080/actuator/health

wait_for_health \
  audit-consumer \
  http://localhost:8081/actuator/health

echo "DEV deployed successfully"
echo "Commit: ${COMMIT_SHA}"

"${COMPOSE[@]}" ps