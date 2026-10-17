
#!/usr/bin/env bash
set -euo pipefail

COMMIT_SHA="${1:?Commit SHA is required}"
REFERENCE="SMOKE-${COMMIT_SHA:0:12}-$(date +%s)-${RANDOM}"
RESPONSE_FILE="$(mktemp)"
trap 'rm -f "$RESPONSE_FILE"' EXIT

echo "Waiting for Transport API..."

ready=false
for _ in {1..60}; do
  if curl --max-time 5 -fsS \
    http://127.0.0.1:8080/actuator/health \
    >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 5
done

if [[ "$ready" != true ]]; then
  echo "Transport API did not become healthy" >&2
  exit 1
fi

HTTP_CODE="$(curl --max-time 15 -sS \
  -o "$RESPONSE_FILE" \
  -w '%{http_code}' \
  -X POST http://127.0.0.1:8080/api/transports \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: dev-smoke-test' \
  -H "X-Request-Id: $REFERENCE" \
  -H "X-Correlation-Id: $REFERENCE" \
  -d "{\"reference\":\"$REFERENCE\"}")"

if [[ "$HTTP_CODE" != "201" ]]; then
  echo "Transport creation failed: HTTP $HTTP_CODE" >&2
  cat "$RESPONSE_FILE" >&2
  exit 1
fi

echo "Transport created: $REFERENCE"
echo "Waiting for CDC audit..."

SQL="SELECT EXISTS (
  SELECT 1 FROM data_audit_log
  WHERE entity_type = 'TRANSPORT'
    AND operation = 'INSERT'
    AND after_data->>'reference' = '$REFERENCE'
);"

for _ in {1..60}; do
  if docker exec audit-postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "$1"' \
    sh "$SQL" 2>/dev/null | grep -qx t; then

    echo "CDC smoke test PASSED: audit INSERT found for $REFERENCE"
    exit 0
  fi
  sleep 5
done

echo "CDC smoke test FAILED: no audit INSERT for $REFERENCE" >&2
docker logs --tail 40 audit-consumer >&2 || true
docker logs --tail 40 transport-kafka-connect >&2 || true
exit 1
