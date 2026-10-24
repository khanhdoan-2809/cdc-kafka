
############ Data Sources ############

data "aws_ssm_parameter" "amazon_linux_2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_partition" "current" {}

############ EC2 IAM Role ############

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

############ SSM Access ############

resource "aws_iam_role_policy_attachment" "ssm" {
  role = aws_iam_role.dev.name

  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

############ ECR Pull Permissions ############

data "aws_iam_policy_document" "dev_ecr_pull" {
  statement {
    sid    = "EcrAuthorization"
    effect = "Allow"

    actions = [
      "ecr:GetAuthorizationToken"
    ]

    resources = ["*"]
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
  name   = "ecr-pull"
  role   = aws_iam_role.dev.id
  policy = data.aws_iam_policy_document.dev_ecr_pull.json
}

############ EC2 Instance Profile ############

resource "aws_iam_instance_profile" "dev" {
  name = "${var.project_name}-${var.environment}-ec2-instance-profile"
  role = aws_iam_role.dev.name
}

############ EC2 Instance ############

resource "aws_instance" "dev" {
  ami           = data.aws_ssm_parameter.amazon_linux_2023.value
  instance_type = var.dev_instance_type

  subnet_id = aws_subnet.public.id

  vpc_security_group_ids = [
    aws_security_group.dev.id
  ]

  iam_instance_profile = aws_iam_instance_profile.dev.name

  # automatically assign a public IPv4 address to an EC2 instance when it launches,
  associate_public_ip_address = true

  root_block_device {
    volume_type = "gp3"
    volume_size = var.dev_root_volume_size

    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint = "enabled"  # Enables IMDS so the EC2 instance can retrieve metadata and temporary IAM role credentials
    http_tokens   = "required" # Requires an IMDSv2 token to access metadata. Requests without a valid token are rejected
  }

  user_data                   = file("${path.module}/user-data.sh")
  user_data_replace_on_change = true

  lifecycle {
    ignore_changes = [
      ami
    ]
  }

  tags = {
    Name = "${var.project_name}-${var.environment}"
  }
}