############ Application Database Secrets ############

resource "aws_secretsmanager_secret" "transport_db_app" {
  name = "${var.project_name}/${var.environment}/database/transport-app"

  recovery_window_in_days = 7

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-db-app"
  }
}

resource "aws_secretsmanager_secret" "audit_db_app" {
  name = "${var.project_name}/${var.environment}/database/audit-app"

  recovery_window_in_days = 7

  tags = {
    Name = "${var.project_name}-${var.environment}-audit-db-app"
  }
}

resource "aws_secretsmanager_secret" "debezium_db" {
  name = "${var.project_name}/${var.environment}/database/debezium"

  recovery_window_in_days = 7

  tags = {
    Name = "${var.project_name}-${var.environment}-debezium-db"
  }
}

############ Bootstrap Security Group ############

resource "aws_security_group" "db_bootstrap" {
  name        = "${var.project_name}-${var.environment}-db-bootstrap"
  description = "Security group for one-off database bootstrap task"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-db-bootstrap-sg"
  }
}

resource "aws_vpc_security_group_egress_rule" "db_bootstrap_outbound" {
  security_group_id = aws_security_group.db_bootstrap.id

  description = "Allow bootstrap task outbound traffic"

  ip_protocol = "-1"
  cidr_ipv4   = "0.0.0.0/0"
}

############ CloudWatch ############

resource "aws_cloudwatch_log_group" "db_bootstrap" {
  name = "/ecs/${var.project_name}/${var.environment}/db-bootstrap"

  retention_in_days = 14

  tags = {
    Name = "${var.project_name}-${var.environment}-db-bootstrap"
  }
}

############ ECS Secret Access ############

data "aws_iam_policy_document" "ecs_db_secret_access" {
  statement {
    effect = "Allow"

    actions = [
      "secretsmanager:GetSecretValue"
    ]

    resources = [
      aws_db_instance.postgres.master_user_secret[0].secret_arn,
      aws_secretsmanager_secret.transport_db_app.arn,
      aws_secretsmanager_secret.audit_db_app.arn,
      aws_secretsmanager_secret.debezium_db.arn
    ]
  }
}

resource "aws_iam_role_policy" "ecs_db_secret_access" {
  name = "database-secrets"

  role = aws_iam_role.ecs_task_execution.id

  policy = data.aws_iam_policy_document.ecs_db_secret_access.json
}

############ Bootstrap SQL ############

locals {
  db_bootstrap_command = <<-EOT
    set -eu

    echo "Bootstrapping production PostgreSQL"

    export PGPASSWORD="$MASTER_PASSWORD"

    psql \
      "host=$DB_HOST port=$DB_PORT dbname=$TRANSPORT_DB_NAME user=$MASTER_USERNAME sslmode=require" \
      --set=transport_password="$TRANSPORT_PASSWORD" \
      --set=audit_password="$AUDIT_PASSWORD" \
      --set=debezium_password="$DEBEZIUM_PASSWORD" \
      --set=transport_db="$TRANSPORT_DB_NAME" \
      --set=audit_db="$AUDIT_DB_NAME" <<'SQL'

    \set ON_ERROR_STOP on

    SELECT format(
      'CREATE ROLE transport_app LOGIN PASSWORD %L',
      :'transport_password'
    )
    WHERE NOT EXISTS (
      SELECT 1
      FROM pg_roles
      WHERE rolname = 'transport_app'
    )
    \gexec

    ALTER ROLE transport_app
      LOGIN
      PASSWORD :'transport_password';

    SELECT format(
      'CREATE ROLE audit_app LOGIN PASSWORD %L',
      :'audit_password'
    )
    WHERE NOT EXISTS (
      SELECT 1
      FROM pg_roles
      WHERE rolname = 'audit_app'
    )
    \gexec

    ALTER ROLE audit_app
      LOGIN
      PASSWORD :'audit_password';

    SELECT format(
      'CREATE ROLE debezium LOGIN PASSWORD %L',
      :'debezium_password'
    )
    WHERE NOT EXISTS (
      SELECT 1
      FROM pg_roles
      WHERE rolname = 'debezium'
    )
    \gexec

    ALTER ROLE debezium
      LOGIN
      PASSWORD :'debezium_password';

    GRANT rds_replication TO debezium;

    ALTER DATABASE :"transport_db"
      OWNER TO transport_app;

    GRANT CONNECT
      ON DATABASE :"transport_db"
      TO transport_app, debezium;

    SELECT format(
      'CREATE DATABASE %I OWNER audit_app',
      :'audit_db'
    )
    WHERE NOT EXISTS (
      SELECT 1
      FROM pg_database
      WHERE datname = :'audit_db'
    )
    \gexec

    ALTER DATABASE :"audit_db"
      OWNER TO audit_app;

    GRANT CONNECT
      ON DATABASE :"audit_db"
      TO audit_app;

    SQL

    echo "Configuring transport database permissions"

    export PGPASSWORD="$TRANSPORT_PASSWORD"

    psql \
      "host=$DB_HOST port=$DB_PORT dbname=$TRANSPORT_DB_NAME user=transport_app sslmode=require" <<'SQL'

    \set ON_ERROR_STOP on

    REVOKE CREATE
      ON SCHEMA public
      FROM PUBLIC;

    GRANT USAGE, CREATE
      ON SCHEMA public
      TO transport_app;

    GRANT USAGE
      ON SCHEMA public
      TO debezium;

    SQL

    echo "Configuring audit database permissions"

    export PGPASSWORD="$AUDIT_PASSWORD"

    psql \
      "host=$DB_HOST port=$DB_PORT dbname=$AUDIT_DB_NAME user=audit_app sslmode=require" <<'SQL'

    \set ON_ERROR_STOP on

    REVOKE CREATE
      ON SCHEMA public
      FROM PUBLIC;

    GRANT USAGE, CREATE
      ON SCHEMA public
      TO audit_app;

    SQL

    echo "Production database bootstrap completed"
  EOT
}

############ One-off Bootstrap Task ############

resource "aws_ecs_task_definition" "db_bootstrap" {
  family = "${var.project_name}-${var.environment}-db-bootstrap"

  requires_compatibilities = [
    "FARGATE"
  ]

  network_mode = "awsvpc"

  cpu    = 256
  memory = 512

  execution_role_arn = aws_iam_role.ecs_task_execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name = "db-bootstrap"

      image = "postgres:17.11-alpine"

      essential = true

      entryPoint = [
        "/bin/sh",
        "-c"
      ]

      command = [
        local.db_bootstrap_command
      ]

      environment = [
        {
          name  = "DB_HOST"
          value = aws_db_instance.postgres.address
        },
        {
          name  = "DB_PORT"
          value = tostring(aws_db_instance.postgres.port)
        },
        {
          name  = "TRANSPORT_DB_NAME"
          value = var.db_name
        },
        {
          name  = "AUDIT_DB_NAME"
          value = var.audit_db_name
        }
      ]

      secrets = [
        {
          name      = "MASTER_USERNAME"
          valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:username::"
        },
        {
          name      = "MASTER_PASSWORD"
          valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:password::"
        },
        {
          name      = "TRANSPORT_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.transport_db_app.arn}:password::"
        },
        {
          name      = "AUDIT_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.audit_db_app.arn}:password::"
        },
        {
          name      = "DEBEZIUM_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.debezium_db.arn}:password::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"

        options = {
          awslogs-group         = aws_cloudwatch_log_group.db_bootstrap.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "bootstrap"
        }
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-${var.environment}-db-bootstrap"
  }
}