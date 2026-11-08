#!/usr/bin/env bash
# Run inside the deployed service with its DB/Redis credentials; never starts a public admin API.
set -euo pipefail
cd "$(dirname "$0")/.."
operation="${1:?Usage: account-admin.sh revoke|set-role|set-enabled|delete UUID [value]}"
account_id="${2:?Account UUID required}"
value="${3:-}"
case "$operation" in
  revoke|delete) [[ $# -eq 2 ]] || { echo 'Unexpected value' >&2; exit 2; } ;;
  set-role) [[ $# -eq 3 && "$value" =~ ^(USER|ADMIN)$ ]] || { echo 'Role must be USER or ADMIN' >&2; exit 2; } ;;
  set-enabled) [[ $# -eq 3 && "$value" =~ ^(true|false)$ ]] || { echo 'Enabled must be true or false' >&2; exit 2; } ;;
  *) echo 'Unsupported account operation' >&2; exit 2 ;;
esac
compose=(docker compose)
if [[ -n "${COMPOSE_ENV_FILE:-}" ]]; then compose+=(--env-file "$COMPOSE_ENV_FILE"); fi
"${compose[@]}" exec -T transport-service java \
  -Dloader.main=com.example.transport.admin.AccountAdminCli \
  -cp /app/transport-service.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  "--account-admin.operation=$operation" "--account-admin.account-id=$account_id"   "--account-admin.value=$value" --spring.main.banner-mode=off --logging.level.root=ERROR
