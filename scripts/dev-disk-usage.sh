#!/usr/bin/env bash

set -euo pipefail

echo "=== Filesystem ==="
df -h /

echo
echo "=== Docker ==="
docker system df

echo
echo "=== Volumes ==="
docker system df -v