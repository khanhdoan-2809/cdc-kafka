#!/usr/bin/env bash

set -euo pipefail

PROJECT_NAME="${PROJECT_NAME:-cdc-kafka}"
AWS_REGION="${AWS_REGION:-ap-southeast-1}"
DEBEZIUM_VERSION="${DEBEZIUM_VERSION:-3.7.0.Final}"

ACCOUNT_ID="$(
  aws sts get-caller-identity \
    --query Account \
    --output text
)"

BUCKET="${PROJECT_NAME}-${ACCOUNT_ID}-${AWS_REGION}-msk-connect-plugins"
KEY="debezium-postgres/${DEBEZIUM_VERSION}/plugin.zip"

WORK_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "${WORK_DIR}"
}

trap cleanup EXIT

mkdir -p "${WORK_DIR}/plugin"
mkdir -p "${WORK_DIR}/debezium"
mkdir -p "${WORK_DIR}/config-provider"

echo "Downloading Debezium PostgreSQL ${DEBEZIUM_VERSION}"

curl \
  --fail \
  --location \
  --output "${WORK_DIR}/debezium.tar.gz" \
  "https://repo1.maven.org/maven2/io/debezium/debezium-connector-postgres/${DEBEZIUM_VERSION}/debezium-connector-postgres-${DEBEZIUM_VERSION}-plugin.tar.gz"

tar \
  -xzf "${WORK_DIR}/debezium.tar.gz" \
  -C "${WORK_DIR}/debezium"

find "${WORK_DIR}/debezium" \
  -type f \
  -name "*.jar" \
  -exec cp {} "${WORK_DIR}/plugin/" \;

echo "Downloading MSK Secrets Manager config provider"

curl \
  --fail \
  --location \
  --output "${WORK_DIR}/config-provider.zip" \
  "https://github.com/aws-samples/msk-config-providers/releases/download/r0.4.0/msk-config-providers-0.4.0-with-dependencies.zip"

unzip \
  -q \
  "${WORK_DIR}/config-provider.zip" \
  -d "${WORK_DIR}/config-provider"

find "${WORK_DIR}/config-provider" \
  -type f \
  -name "*.jar" \
  -exec cp {} "${WORK_DIR}/plugin/" \;

echo "Creating plugin ZIP"

(
  cd "${WORK_DIR}/plugin"
  zip -qr "${WORK_DIR}/plugin.zip" .
)

echo "Uploading s3://${BUCKET}/${KEY}"

aws s3 cp \
  "${WORK_DIR}/plugin.zip" \
  "s3://${BUCKET}/${KEY}"

echo
echo "Plugin uploaded successfully"
echo "s3://${BUCKET}/${KEY}"