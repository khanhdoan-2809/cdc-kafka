#!/usr/bin/env bash

set -euo pipefail

ACTION="${1:-status}"

cd /opt/cdc-kafka

if [[ ! -f .env.dev ]]; then
  echo ".env.dev does not exist"
  exit 1
fi

export TRANSPORT_IMAGE="$(
  docker inspect transport-service \
    --format '{{.Config.Image}}'
)"

export AUDIT_IMAGE="$(
  docker inspect audit-consumer \
    --format '{{.Config.Image}}'
)"

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
)

case "${ACTION}" in
  start)
    echo "Enabling DLT replay"

    export AUDIT_DLT_REPLAY_ENABLED=true

    "${COMPOSE[@]}" up \
      -d \
      --no-deps \
      --force-recreate \
      audit-consumer

    echo "DLT replay enabled"
    ;;

  stop)
    echo "Disabling DLT replay"

    export AUDIT_DLT_REPLAY_ENABLED=false

    "${COMPOSE[@]}" up \
      -d \
      --no-deps \
      --force-recreate \
      audit-consumer

    echo "DLT replay disabled"
    ;;

  status)
    docker inspect audit-consumer \
      --format '{{range .Config.Env}}{{println .}}{{end}}' \
      | grep '^AUDIT_DLT_REPLAY_ENABLED=' \
      || true
    ;;

  *)
    echo "Usage: $0 {start|stop|status}"
    exit 1
    ;;
esac