#!/usr/bin/env bash

set -euo pipefail

ACTION="${1:-status}"

cd /opt/cdc-kafka

if [[ ! -f .env.dev ]]; then
  echo ".env.dev not found"
  exit 1
fi

AUDIT_CONTAINER="$(
  docker compose ps -q audit-consumer \
    | head -n 1
)"

if [[ -z "${AUDIT_CONTAINER}" ]]; then
  echo "audit-consumer is not running"
  exit 1
fi

export AUDIT_IMAGE="$(
  docker inspect "${AUDIT_CONTAINER}" \
    --format '{{.Config.Image}}'
)"

TRANSPORT_CONTAINER="$(
  docker compose ps -q transport-service \
    | head -n 1
)"

if [[ -n "${TRANSPORT_CONTAINER}" ]]; then
  export TRANSPORT_IMAGE="$(
    docker inspect "${TRANSPORT_CONTAINER}" \
      --format '{{.Config.Image}}'
  )"
fi

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

COMPOSE=(
  docker compose
  --env-file .env.dev
  -f compose.yml
  -f compose.dev.yml
  --profile ops
)

case "${ACTION}" in
  start)
    echo "Starting DLT replay worker"

    "${COMPOSE[@]}" up \
      -d \
      --no-deps \
      audit-dlt-replay

    echo
    "${COMPOSE[@]}" ps audit-dlt-replay
    ;;

  stop)
    echo "Stopping DLT replay worker"

    "${COMPOSE[@]}" stop audit-dlt-replay
    "${COMPOSE[@]}" rm -f audit-dlt-replay

    echo "DLT replay worker stopped"
    ;;

  status)
    "${COMPOSE[@]}" ps audit-dlt-replay
    ;;

  logs)
    "${COMPOSE[@]}" logs \
      -f \
      audit-dlt-replay
    ;;

  *)
    echo "Usage: $0 {start|stop|status|logs}"
    exit 1
    ;;
esac