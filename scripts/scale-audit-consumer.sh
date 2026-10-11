#!/usr/bin/env bash

set -euo pipefail

REPLICAS="${1:-}"

if [[ -z "${REPLICAS}" ]]; then
  echo "Usage: $0 {1|2|3}"
  exit 1
fi

case "${REPLICAS}" in
  1|2|3)
    ;;
  *)
    echo "Supported replica counts: 1, 2, 3"
    exit 1
    ;;
esac

echo "Scaling audit-consumer to ${REPLICAS} replica(s)"

docker compose up \
  -d \
  --no-build \
  --scale "audit-consumer=${REPLICAS}" \
  audit-consumer

echo
docker compose ps audit-consumer