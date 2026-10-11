############ ECS Cluster ############

resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-${var.environment}"

  tags = {
    Name = "${var.project_name}-${var.environment}-ecs"
  }
}

############ CloudWatch Logs ############

resource "aws_cloudwatch_log_group" "transport" {
  name = "/ecs/${var.project_name}/${var.environment}/transport-service"

  retention_in_days = 14

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-logs"
  }
}

############ ECS Task Execution Role ############

data "aws_iam_policy_document" "ecs_task_execution_assume_role" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRole"
    ]

    principals {
      type = "Service"

      identifiers = [
        "ecs-tasks.amazonaws.com"
      ]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name = "${var.project_name}-${var.environment}-ecs-execution"

  assume_role_policy = data.aws_iam_policy_document.ecs_task_execution_assume_role.json

  tags = {
    Name = "${var.project_name}-${var.environment}-ecs-execution"
  }
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role = aws_iam_role.ecs_task_execution.name

  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

############ Transport Task Definition ############

resource "aws_ecs_task_definition" "transport" {
  family = "${var.project_name}-${var.environment}-transport-service"

  requires_compatibilities = [
    "FARGATE"
  ]

  network_mode = "awsvpc"

  cpu    = var.transport_task_cpu
  memory = var.transport_task_memory

  execution_role_arn = aws_iam_role.ecs_task_execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name = "transport-service"

      # Terraform creates the ECS infrastructure before the first real
      # production release exists. GitHub Actions replaces this image
      # before scaling the service above zero.
      image = "${aws_ecr_repository.app["transport_service"].repository_url}:bootstrap"

      essential = true

      portMappings = [
        {
          containerPort = var.transport_container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SERVER_PORT"
          value = tostring(var.transport_container_port)
        },
        {
          name  = "DB_HOST"
          value = aws_db_instance.postgres.address
        },
        {
          name  = "DB_PORT"
          value = tostring(aws_db_instance.postgres.port)
        },
        {
          name  = "DB_NAME"
          value = var.db_name
        },
        {
          name  = "TRANSPORT_DB_MAX_POOL_SIZE"
          value = "10"
        },
        {
          name  = "TRANSPORT_DB_MIN_IDLE"
          value = "2"
        },
        {
          name  = "TRANSPORT_DB_CONNECTION_TIMEOUT_MS"
          value = "30000"
        }
      ]

      secrets = [
        {
          name      = "DB_USERNAME"
          valueFrom = "${aws_secretsmanager_secret.transport_db_app.arn}:username::"
        },
        {
          name      = "DB_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.transport_db_app.arn}:password::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"

        options = {
          awslogs-group         = aws_cloudwatch_log_group.transport.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "transport"
        }
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-service"
  }
}

############ Transport ECS Service ############

resource "aws_ecs_service" "transport" {
  name = "transport-service"

  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.transport.arn

  # Keep the service at zero until release.yml deploys a real image.
  desired_count = 0

  launch_type = "FARGATE"

  network_configuration {
    subnets = aws_subnet.app[*].id

    security_groups = [
      aws_security_group.transport_ecs.id
    ]

    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.transport.arn
    container_name   = "transport-service"
    container_port   = var.transport_container_port
  }

  health_check_grace_period_seconds = 60

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  enable_ecs_managed_tags = true

  lifecycle {
    # GitHub Actions owns application releases and scaling.
    ignore_changes = [
      task_definition,
      desired_count
    ]
  }

  depends_on = [
    aws_lb_listener.http
  ]

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-service"
  }
}