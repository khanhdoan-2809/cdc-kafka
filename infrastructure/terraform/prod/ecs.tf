resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-${var.environment}"

  tags = {
    Name = "${var.project_name}-${var.environment}-ecs"
  }
}

# CloudWatch log group
resource "aws_cloudwatch_log_group" "transport" {
  name = "/ecs/${var.project_name}/${var.environment}/transport-service"

  retention_in_days = 14

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-logs"
  }
}

# ECS task execution role
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
}

# Task Definition
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

      image = "${aws_ecr_repository.app["transport_service"].repository_url}:bootstrap"

      essential = true

      portMappings = [
        {
          containerPort = var.transport_container_port
          hostPort      = var.transport_container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SERVER_PORT"
          value = tostring(var.transport_container_port)
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

# ECS Service
resource "aws_ecs_service" "transport" {
  name = "transport-service"

  cluster = aws_ecs_cluster.main.id

  task_definition = aws_ecs_task_definition.transport.arn

  desired_count = var.transport_desired_count

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

    container_name = "transport-service"
    container_port = var.transport_container_port
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  health_check_grace_period_seconds = 60

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  enable_ecs_managed_tags = true

  depends_on = [
    aws_lb_listener.http
  ]

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-service"
  }
}