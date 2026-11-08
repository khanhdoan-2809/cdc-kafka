#!/usr/bin/env bash
# Creates local/DEV credentials once; never use this script to rotate production keys.
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
mkdir -p secrets/jwt
if [[ -e secrets/jwt/private-key.pem || -e secrets/jwt/public.pem ]]; then
  [[ -s secrets/jwt/private-key.pem && -s secrets/jwt/public.pem ]] || {
    echo 'Incomplete key pair; restore it or deliberately replace both files' >&2; exit 1;
  }
else
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secrets/jwt/private-key.pem 2>/dev/null
  openssl pkey -in secrets/jwt/private-key.pem -pubout -out secrets/jwt/public.pem
fi
if [[ ! -f secrets/auth.env ]]; then
  monitoring_password="$(openssl rand -hex 24)"
  {
    printf 'JWT_ISSUER=https://cdc-kafka.local/auth\nJWT_AUDIENCE=transport-api\n'
    printf 'AUTH_BOOTSTRAP_USERNAME=admin\nAUTH_BOOTSTRAP_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'AUTH_BOOTSTRAP_EMAIL=admin@cdc-kafka.local\n'
    printf 'MONITORING_PASSWORD=%s\n' "$monitoring_password"
  } > secrets/auth.env
  printf '%s' "$monitoring_password" > secrets/monitoring-password
fi
[[ -s secrets/monitoring-password ]] || { echo 'Missing monitoring password file' >&2; exit 1; }
echo 'DEV authentication files are ready under secrets/. Credentials are not printed.'
