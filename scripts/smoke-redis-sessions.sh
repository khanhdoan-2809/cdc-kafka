#!/usr/bin/env bash
# Use a dedicated verified test account: this deliberately logs out every session for that account.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${SMOKE_USERNAME:?Dedicated verified test username required}"
: "${SMOKE_PASSWORD:?Test account password required}"
base="${SMOKE_BASE_URL:-http://127.0.0.1:8080}"
compose=(docker compose)
if [[ -n "${COMPOSE_ENV_FILE:-}" ]]; then compose+=(--env-file "$COMPOSE_ENV_FILE"); fi
login() {
  jq -n '{username:env.SMOKE_USERNAME,password:env.SMOKE_PASSWORD}' |
    curl -fsS --max-time 15 -H 'Content-Type: application/json' --data-binary @- "$base/api/auth/login" |
    jq -er '.accessToken'
}
expect() {
  local method="$1" path="$2" token="$3" expected="$4" actual
  actual="$(curl -sS --max-time 15 -o /dev/null -w '%{http_code}' -X "$method"     -H "Authorization: Bearer $token" "$base$path")"
  [[ "$actual" == "$expected" ]] || { echo "Unexpected status $actual for $path (expected $expected)" >&2; exit 1; }
}
wait_ready() {
  for attempt in $(seq 1 60); do
    if curl -fsS --max-time 2 "$base/actuator/health/readiness" >/dev/null 2>&1; then return; fi
    sleep 1
  done
  echo 'Application did not become ready' >&2; exit 1
}
one="$(login)"; two="$(login)"
expect GET /api/auth/me "$one" 200
expect GET /api/auth/me "$two" 200
expect POST /api/auth/logout "$one" 204
expect GET /api/auth/me "$one" 401
expect GET /api/auth/me "$two" 401
fresh="$(login)"
expect GET /api/auth/me "$fresh" 200
# Optional and disruptive: use ONLY on an isolated Compose smoke stack.
if [[ "${SMOKE_RESTART_SERVICES:-false}" == true ]]; then
  "${compose[@]}" restart transport-service >/dev/null
  wait_ready
  expect GET /api/auth/me "$fresh" 200
  "${compose[@]}" restart redis >/dev/null
  wait_ready
  expect GET /api/auth/me "$fresh" 401
  fresh="$(login)"
  expect GET /api/auth/me "$fresh" 200
fi
expect POST /api/auth/logout "$fresh" 204
unset one two fresh
echo 'Redis session smoke passed.'
