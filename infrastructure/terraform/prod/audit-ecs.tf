############ Audit Consumer Security Group ############

resource "aws_security_group" "audit_ecs" {
  name        = "${var.project_name}-${var.environment}-audit-ecs"
  description = "Security group for audit-consumer ECS"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-audit-ecs"
  }
}

resource "aws_vpc_security_group_egress_rule" "audit_outbound" {
  security_group_id = aws_security_group.audit_ecs.id

  description = "Allow audit consumer outbound traffic"

  ip_protocol = "-1"
  cidr_ipv4   = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_audit" {
  security_group_id = aws_security_group.rds.id

  referenced_security_group_id = aws_security_group.audit_ecs.id

  description = "Allow audit consumer to access PostgreSQL"

  ip_protocol = "tcp"
  from_port   = 5432
  to_port     = 5432
}

############ CloudWatch ############

resource "aws_cloudwatch_log_group" "audit" {
  name              = "/ecs/${var.project_name}/${var.environment}/audit-consumer"
  retention_in_days = 14

  tags = {
    Name = "${var.project_name}-${var.environment}-audit-logs"
  }
}

############ ECS Task Role ############

resource "aws_iam_role" "audit_task" {
  name = "${var.project_name}-${var.environment}-audit-task"

  assume_role_policy = data.aws_iam_policy_document.ecs_task_execution_assume_role.json

  tags = {
    Name = "${var.project_name}-${var.environment}-audit-task"
  }
}

############ MSK IAM Permissions ############

locals {
  audit_source_topics = [
    "transport.public.transport",
    "transport.public.job",
    "transport.public.container"
  ]

  audit_dlt_topics = [
    for topic in local.audit_source_topics : "${topic}.dlt"
  ]

  audit_all_topics = concat(
    local.audit_source_topics,
    local.audit_dlt_topics
  )
}

data "aws_iam_policy_document" "audit_msk" {
  statement {
    sid    = "ConnectToMSK"
    effect = "Allow"

    actions = [
      "kafka-cluster:Connect",
      "kafka-cluster:DescribeCluster",
      "kafka-cluster:WriteDataIdempotently"
    ]

    resources = [
      aws_msk_serverless_cluster.main.arn
    ]
  }

  statement {
    sid    = "ReadKafkaTopics"
    effect = "Allow"

    actions = [
      "kafka-cluster:DescribeTopic",
      "kafka-cluster:ReadData"
    ]

    resources = [
      for topic in local.audit_all_topics :
      "${local.msk_topic_arn_prefix}/${topic}"
    ]
  }

  statement {
    sid    = "PublishAndCreateDLT"
    effect = "Allow"

    actions = [
      "kafka-cluster:CreateTopic",
      "kafka-cluster:WriteData"
    ]

    resources = [
      for topic in local.audit_dlt_topics :
      "${local.msk_topic_arn_prefix}/${topic}"
    ]
  }

  statement {
    sid    = "KafkaConsumerGroups"
    effect = "Allow"

    actions = [
      "kafka-cluster:DescribeGroup",
      "kafka-cluster:AlterGroup"
    ]

    resources = [
      "${local.msk_group_arn_prefix}/audit-consumer",
      "${local.msk_group_arn_prefix}/audit-dlt-replay"
    ]
  }
}

resource "aws_iam_role_policy" "audit_msk" {
  name   = "msk-access"
  role   = aws_iam_role.audit_task.id
  policy = data.aws_iam_policy_document.audit_msk.json
}

############ ECS Task Definition ############

resource "aws_ecs_task_definition" "audit" {
  family = "${var.project_name}-${var.environment}-audit-consumer"

  requires_compatibilities = ["FARGATE"]

  network_mode = "awsvpc"

  cpu    = 512
  memory = 1024

  execution_role_arn = aws_iam_role.ecs_task_execution.arn
  task_role_arn      = aws_iam_role.audit_task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name = "audit-consumer"

      # Replaced with a real release image by GitHub Actions.
      image = "${aws_ecr_repository.app["audit_consumer"].repository_url}:bootstrap"

      essential = true

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "prod"
        },
        {
          name  = "SERVER_PORT"
          value = "8081"
        },
        {
          name  = "AUDIT_DB_HOST"
          value = aws_db_instance.postgres.address
        },
        {
          name  = "AUDIT_DB_PORT"
          value = tostring(aws_db_instance.postgres.port)
        },
        {
          name  = "AUDIT_DB_NAME"
          value = var.audit_db_name
        },
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.postgres.address}:${aws_db_instance.postgres.port}/${var.audit_db_name}?sslmode=require"
        },
        {
          name  = "KAFKA_BOOTSTRAP_SERVERS"
          value = aws_msk_serverless_cluster.main.bootstrap_brokers_sasl_iam
        },
        {
          name  = "AUDIT_DLT_REPLICATION_FACTOR"
          value = "-1"
        },
        {
          name  = "AUDIT_DB_MAX_POOL_SIZE"
          value = "5"
        },
        {
          name  = "AUDIT_DB_MIN_IDLE"
          value = "1"
        },
        {
          name  = "AUDIT_KAFKA_CONCURRENCY"
          value = "3"
        },
        {
          name  = "AUDIT_DLT_REPLAY_ENABLED"
          value = "false"
        }
      ]

      secrets = [
        {
          name      = "AUDIT_DB_USERNAME"
          valueFrom = "${aws_secretsmanager_secret.audit_db_app.arn}:username::"
        },
        {
          name      = "AUDIT_DB_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.audit_db_app.arn}:password::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"

        options = {
          awslogs-group         = aws_cloudwatch_log_group.audit.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "audit"
        }
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-${var.environment}-audit-consumer"
  }
}

############ ECS Service ############

resource "aws_ecs_service" "audit" {
  name = "audit-consumer"

  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.audit.arn

  # The first production release starts this service.
  desired_count = 0

  launch_type = "FARGATE"

  network_configuration {
    subnets = aws_subnet.app[*].id

    security_groups = [
      aws_security_group.audit_ecs.id,
      aws_security_group.msk_client.id
    ]

    assign_public_ip = false
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  enable_ecs_managed_tags = true

  lifecycle {
    # Releases and scaling are managed by GitHub Actions.
    ignore_changes = [
      task_definition,
      desired_count
    ]
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-audit-consumer"
  }
}