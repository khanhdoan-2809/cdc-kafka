#!/usr/bin/env bash

set -euo pipefail

TF_DIR="${TF_DIR:-infrastructure/terraform/prod}"
AWS_REGION="${AWS_REGION:?AWS_REGION is required}"

ensure_secret() {
  local secret_arn="$1"
  local username="$2"
  local database="$3"

  if aws secretsmanager get-secret-value \
    --region "${AWS_REGION}" \
    --secret-id "${secret_arn}" \
    >/dev/null 2>&1; then

    echo "Secret already initialized: ${username}"
    return
  fi

  echo "Initializing secret: ${username}"

  local password
  password="$(
    aws secretsmanager get-random-password \
      --region "${AWS_REGION}" \
      --password-length 32 \
      --exclude-punctuation \
      --query RandomPassword \
      --output text
  )"

  local temp_file
  temp_file="$(mktemp)"

  chmod 600 "${temp_file}"

  jq -n \
    --arg username "${username}" \
    --arg password "${password}" \
    --arg dbname "${database}" \
    '{
      username: $username,
      password: $password,
      dbname: $dbname
    }' > "${temp_file}"

  aws secretsmanager put-secret-value \
    --region "${AWS_REGION}" \
    --secret-id "${secret_arn}" \
    --secret-string "file://${temp_file}" \
    >/dev/null

  rm -f "${temp_file}"
  unset password
}

TRANSPORT_SECRET="$(
  terraform -chdir="${TF_DIR}" \
    output -raw transport_db_secret_arn
)"

AUDIT_SECRET="$(
  terraform -chdir="${TF_DIR}" \
    output -raw audit_db_secret_arn
)"

DEBEZIUM_SECRET="$(
  terraform -chdir="${TF_DIR}" \
    output -raw debezium_db_secret_arn
)"

CLUSTER="$(
  terraform -chdir="${TF_DIR}" \
    output -raw ecs_cluster_name
)"

TASK_DEFINITION="$(
  terraform -chdir="${TF_DIR}" \
    output -raw db_bootstrap_task_definition_arn
)"

SECURITY_GROUP="$(
  terraform -chdir="${TF_DIR}" \
    output -raw db_bootstrap_security_group_id
)"

LOG_GROUP="$(
  terraform -chdir="${TF_DIR}" \
    output -raw db_bootstrap_log_group_name
)"

SUBNETS="$(
  terraform -chdir="${TF_DIR}" \
    output -json app_subnet_ids \
    | jq -r 'join(",")'
)"

ensure_secret \
  "${TRANSPORT_SECRET}" \
  "transport_app" \
  "transport"

ensure_secret \
  "${AUDIT_SECRET}" \
  "audit_app" \
  "audit"

ensure_secret \
  "${DEBEZIUM_SECRET}" \
  "debezium" \
  "transport"

echo
echo "Running database bootstrap task"

RUN_RESULT="$(
  aws ecs run-task \
    --region "${AWS_REGION}" \
    --cluster "${CLUSTER}" \
    --launch-type FARGATE \
    --task-definition "${TASK_DEFINITION}" \
    --network-configuration \
      "awsvpcConfiguration={subnets=[${SUBNETS}],securityGroups=[${SECURITY_GROUP}],assignPublicIp=DISABLED}"
)"

FAILURES="$(
  jq '.failures | length' <<< "${RUN_RESULT}"
)"

if [[ "${FAILURES}" != "0" ]]; then
  echo "${RUN_RESULT}" | jq '.failures'
  exit 1
fi

TASK_ARN="$(
  jq -r '.tasks[0].taskArn' <<< "${RUN_RESULT}"
)"

echo "Task: ${TASK_ARN}"

aws ecs wait tasks-stopped \
  --region "${AWS_REGION}" \
  --cluster "${CLUSTER}" \
  --tasks "${TASK_ARN}"

TASK_RESULT="$(
  aws ecs describe-tasks \
    --region "${AWS_REGION}" \
    --cluster "${CLUSTER}" \
    --tasks "${TASK_ARN}"
)"

EXIT_CODE="$(
  jq -r '.tasks[0].containers[0].exitCode // -1' \
    <<< "${TASK_RESULT}"
)"

echo
echo "Bootstrap logs"

aws logs tail "${LOG_GROUP}" \
  --region "${AWS_REGION}" \
  --since 10m \
  --format short \
  || true

if [[ "${EXIT_CODE}" != "0" ]]; then
  echo
  echo "Database bootstrap failed"
  echo "${TASK_RESULT}" | jq '.tasks[0].stoppedReason'
  exit 1
fi

echo
echo "Database bootstrap completed successfully"