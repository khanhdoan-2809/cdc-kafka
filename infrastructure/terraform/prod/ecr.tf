locals {
  ecr_repositories = {
    transport_service = "${var.project_name}-transport-service"
    audit_consumer    = "${var.project_name}-audit-consumer"
  }
}

resource "aws_ecr_repository" "app" {
  for_each = local.ecr_repositories

  name = each.value

  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true # vulnerability scanning
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = each.value
  }
}

# We don't want failed builds and intermediate manifests leaving untagged images forever.
resource "aws_ecr_lifecycle_policy" "app" {
  for_each = aws_ecr_repository.app

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1

        description = "Delete untagged images older than 7 days"

        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }

        action = {
          type = "expire"
        }
      }
    ]
  })
}