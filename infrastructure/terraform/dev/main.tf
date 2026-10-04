data "aws_ssm_parameter" "amazon_linux_2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr

  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.project_name}-${var.environment}-vpc"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-igw"
  }
}

resource "aws_subnet" "public" {
  vpc_id = aws_vpc.main.id

  cidr_block = cidrsubnet(var.vpc_cidr, 8, 0)

  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-${var.environment}-public"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-public-rt"
  }
}

resource "aws_route" "internet" {
  route_table_id = aws_route_table.public.id

  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "dev" {
  name        = "${var.project_name}-${var.environment}"
  description = "Security group for cdc-kafka development EC2"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-sg"
  }
}

resource "aws_vpc_security_group_egress_rule" "dev_outbound" {
  security_group_id = aws_security_group.dev.id

  ip_protocol = "-1"
  cidr_ipv4   = "0.0.0.0/0"

  description = "Allow development EC2 outbound traffic"
}

data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRole"
    ]

    principals {
      type = "Service"

      identifiers = [
        "ec2.amazonaws.com"
      ]
    }
  }
}

resource "aws_iam_role" "dev" {
  name = "${var.project_name}-${var.environment}-ec2"

  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role = aws_iam_role.dev.name

  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "dev" {
  name = "${var.project_name}-${var.environment}-ec2"

  role = aws_iam_role.dev.name
}

resource "aws_instance" "dev" {
  ami           = data.aws_ssm_parameter.amazon_linux_2023.value
  instance_type = var.dev_instance_type

  subnet_id = aws_subnet.public.id

  vpc_security_group_ids = [
    aws_security_group.dev.id
  ]

  iam_instance_profile = aws_iam_instance_profile.dev.name

  associate_public_ip_address = true

  root_block_device {
    volume_type = "gp3"
    volume_size = var.dev_root_volume_size

    encrypted = true

    delete_on_termination = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  user_data = <<-EOF
    #!/bin/bash
    set -eux

    dnf install -y docker

    systemctl enable --now docker
    systemctl enable --now amazon-ssm-agent

    usermod -aG docker ec2-user

    mkdir -p /opt/cdc-kafka
    chown ec2-user:ec2-user /opt/cdc-kafka
  EOF

  lifecycle {
    ignore_changes = [
      ami
    ]
  }

  tags = {
    Name = "${var.project_name}-${var.environment}"
  }
}

# EC2 pull from ECR
data "aws_partition" "current" {}

data "aws_iam_policy_document" "dev_ecr_pull" {
  statement {
    sid    = "EcrAuthorization"
    effect = "Allow"

    actions = [
      "ecr:GetAuthorizationToken"
    ]

    resources = [
      "*"
    ]
  }

  statement {
    sid    = "PullApplicationImages"
    effect = "Allow"

    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer"
    ]

    resources = [
      "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${var.aws_account_id}:repository/${var.project_name}-transport-service",
      "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${var.aws_account_id}:repository/${var.project_name}-audit-consumer"
    ]
  }
}

resource "aws_iam_role_policy" "dev_ecr_pull" {
  name = "ecr-pull"
  role = aws_iam_role.dev.id

  policy = data.aws_iam_policy_document.dev_ecr_pull.json
}

# User Data
user_data = <<-EOF
  #!/bin/bash
  set -eux

  dnf install -y docker git curl openssl

  systemctl enable --now docker
  systemctl enable --now amazon-ssm-agent

  usermod -aG docker ec2-user

  mkdir -p /usr/local/lib/docker/cli-plugins

  curl -fsSL \
    https://github.com/docker/compose/releases/download/v5.6.0/docker-compose-linux-x86_64 \
    -o /usr/local/lib/docker/cli-plugins/docker-compose

  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

  mkdir -p /opt/cdc-kafka

  chown -R ec2-user:ec2-user /opt/cdc-kafka
EOF

data "aws_partition" "current" {}

data "aws_iam_policy_document" "dev_ecr_pull" {
  statement {
    effect = "Allow"

    actions = [
      "ecr:GetAuthorizationToken"
    ]

    resources = [
      "*"
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer"
    ]

    resources = [
      "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${var.aws_account_id}:repository/${var.project_name}-transport-service",
      "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${var.aws_account_id}:repository/${var.project_name}-audit-consumer"
    ]
  }
}

resource "aws_iam_role_policy" "dev_ecr_pull" {
  name = "ecr-pull"
  role = aws_iam_role.dev.id

  policy = data.aws_iam_policy_document.dev_ecr_pull.json
}