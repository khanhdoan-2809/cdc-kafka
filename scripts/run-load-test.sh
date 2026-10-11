#!/usr/bin/env bash

set -euo pipefail

PROFILE="${1:-baseline}"

case "${PROFILE}" in
  baseline|stress|spike)
    ;;
  *)
    echo "Usage: $0 {baseline|stress|spike}"
    exit 1
    ;;
esac

echo "Checking transport-service..."

if ! curl -fsS http://localhost:8080/actuator/health >/dev/null; then
  echo "transport-service is not healthy"
  exit 1
fi

echo
echo "Running k6 profile: ${PROFILE}"
echo

export K6_PROFILE="${PROFILE}"

docker compose \
  -f compose.yml \
  -f compose.load.yml \
  --profile load \
  run \
  --rm \
  --no-deps \
  k6