#!/bin/bash
set -euo pipefail

exec > >(tee -a /var/log/cdc-kafka-bootstrap.log) 2>&1

echo "Starting EC2 initialization..."

# 1. Install system dependencies
for attempt in 1 2 3 4 5; do
  if dnf install -y \
    docker \
    git \
    curl \
    openssl \
    unzip; then
    break
  fi

  if [ "$attempt" -eq 5 ]; then
    echo "Package installation failed"
    exit 1
  fi

  sleep 15
done

# 2. Install AWS CLI v2 if unavailable
if ! command -v aws >/dev/null 2>&1; then
  TMP_DIR=$(mktemp -d)

  curl -fL --retry 5 \
    https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip \
    -o "$TMP_DIR/awscliv2.zip"

  (
    cd "$TMP_DIR"
    unzip -q awscliv2.zip
    ./aws/install
  )

  rm -rf "$TMP_DIR"
fi

# 3. Enable Docker
systemctl enable --now docker

# 4. Enable SSM Agent
systemctl enable --now amazon-ssm-agent

# 5. Allow ec2-user to use Docker
usermod -aG docker ec2-user

# 6. Install Docker Compose plugin
COMPOSE_VERSION="v5.6.0"

PLUGIN_DIR="/usr/local/lib/docker/cli-plugins"
mkdir -p "$PLUGIN_DIR"

curl -fL --retry 5 \
  "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o "$PLUGIN_DIR/docker-compose"

chmod +x "$PLUGIN_DIR/docker-compose"

# 7. Prepare application directory
mkdir -p /opt

# 8. Verify installed tools
git --version
docker --version
docker compose version
aws --version
openssl version
curl --version

# 9. Verify Docker daemon is running
for attempt in $(seq 1 30); do
  if docker info >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

docker info >/dev/null

# 10. Mark initialization as completed
mkdir -p /var/lib/cdc-kafka
touch /var/lib/cdc-kafka/bootstrap-ready

echo "EC2 initialization completed successfully"