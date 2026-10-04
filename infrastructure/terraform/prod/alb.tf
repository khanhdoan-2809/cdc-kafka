resource "aws_security_group" "alb" {
  name        = "${var.project_name}-${var.environment}-alb"
  description = "Security group for transport-service ALB"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-alb-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id

  description = "Allow public HTTP"

  ip_protocol = "tcp"
  from_port   = 80
  to_port     = 80

  cidr_ipv4 = "0.0.0.0/0"
}

resource "aws_security_group" "transport_ecs" {
  name        = "${var.project_name}-${var.environment}-transport-ecs"
  description = "Security group for transport-service ECS tasks"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-ecs-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "transport_from_alb" {
  security_group_id = aws_security_group.transport_ecs.id

  referenced_security_group_id = aws_security_group.alb.id

  description = "Allow ALB to reach transport-service"

  ip_protocol = "tcp"
  from_port   = var.transport_container_port
  to_port     = var.transport_container_port
}

resource "aws_vpc_security_group_egress_rule" "alb_to_transport" {
  security_group_id = aws_security_group.alb.id

  referenced_security_group_id = aws_security_group.transport_ecs.id

  description = "Allow ALB to reach transport-service"

  ip_protocol = "tcp"
  from_port   = var.transport_container_port
  to_port     = var.transport_container_port
}

resource "aws_vpc_security_group_egress_rule" "transport_outbound" {
  security_group_id = aws_security_group.transport_ecs.id

  description = "Allow transport-service outbound traffic"

  ip_protocol = "-1"
  cidr_ipv4   = "0.0.0.0/0"
}

# Application Load Balancer
resource "aws_lb" "transport" {
  name = "${var.project_name}-${var.environment}"

  load_balancer_type = "application"

  internal = false

  security_groups = [
    aws_security_group.alb.id
  ]

  subnets = aws_subnet.public[*].id

  tags = {
    Name = "${var.project_name}-${var.environment}-alb"
  }
}

# Target Group
resource "aws_lb_target_group" "transport" {
  name = "${var.project_name}-${var.environment}-transport"

  port     = var.transport_container_port
  protocol = "HTTP"

  vpc_id = aws_vpc.main.id

  target_type = "ip"

  deregistration_delay = 30

  health_check {
    enabled = true

    path     = "/actuator/health"
    protocol = "HTTP"

    port = "traffic-port"

    matcher = "200"

    interval = 30
    timeout  = 5

    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-tg"
  }
}

# HTTP listener
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.transport.arn

  port     = 80
  protocol = "HTTP"

  default_action {
    type = "forward"

    target_group_arn = aws_lb_target_group.transport.arn
  }
}