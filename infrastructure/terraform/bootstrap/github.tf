data "aws_partition" "current" {}

resource "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"

  client_id_list = [
    "sts.amazonaws.com"
  ]
}

locals {
  github_pull_request_subjects = [
    "repo:${var.github_owner}/${var.github_repository}:pull_request",
    "repo:${var.github_owner}@*/${var.github_repository}@*:pull_request"
  ]

  github_production_subjects = [
    "repo:${var.github_owner}/${var.github_repository}:environment:${var.github_environment}",
    "repo:${var.github_owner}@*/${var.github_repository}@*:environment:${var.github_environment}"
  ]
}

data "aws_iam_policy_document" "github_plan_assume_role" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRoleWithWebIdentity"
    ]

    principals {
      type = "Federated"

      identifiers = [
        aws_iam_openid_connect_provider.github.arn
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"

      values = [
        "sts.amazonaws.com"
      ]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"

      values = local.github_pull_request_subjects
    }
  }
}

resource "aws_iam_role" "github_terraform_plan" {
  name = "${var.project_name}-github-terraform-plan"

  assume_role_policy = data.aws_iam_policy_document.github_plan_assume_role.json

  max_session_duration = 3600
}

############ RO Access ############
resource "aws_iam_role_policy_attachment" "github_plan_read_only" {
  role = aws_iam_role.github_terraform_plan.name

  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/ReadOnlyAccess"
}

############ Read and Lock State S3 File ############
data "aws_iam_policy_document" "github_plan_state" {
  statement {
    effect = "Allow"

    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket"
    ]

    resources = [
      aws_s3_bucket.terraform_state.arn
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "s3:GetObject"
    ]

    resources = [
      "${aws_s3_bucket.terraform_state.arn}/prod/terraform.tfstate"
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]

    resources = [
      "${aws_s3_bucket.terraform_state.arn}/prod/terraform.tfstate.tflock"
    ]
  }
}

resource "aws_iam_role_policy" "github_plan_state" {
  name = "terraform-state"

  role = aws_iam_role.github_terraform_plan.id

  policy = data.aws_iam_policy_document.github_plan_state.json
}

############ Terraform Apply Role ############
data "aws_iam_policy_document" "github_apply_assume_role" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRoleWithWebIdentity"
    ]

    principals {
      type = "Federated"

      identifiers = [
        aws_iam_openid_connect_provider.github.arn
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"

      values = [
        "sts.amazonaws.com"
      ]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"

      values = local.github_production_subjects
    }
  }
}

resource "aws_iam_role" "github_terraform_apply" {
  name = "${var.project_name}-github-terraform-apply"

  assume_role_policy = data.aws_iam_policy_document.github_apply_assume_role.json

  max_session_duration = 3600
}

############ PowerUserAccess ############
resource "aws_iam_role_policy_attachment" "github_apply_power_user" {
  role = aws_iam_role.github_terraform_apply.name

  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/PowerUserAccess"
}

############ Limited IAM permissions ############
data "aws_iam_policy_document" "github_apply_iam" {
  statement {
    sid    = "ManageProjectRoles"
    effect = "Allow"

    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",

      "iam:PutRolePolicy",
      "iam:GetRolePolicy",
      "iam:DeleteRolePolicy",

      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",

      "iam:PassRole"
    ]

    resources = [
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/${var.project_name}-*"
    ]
  }

  statement {
    sid    = "ManageProjectPolicies"
    effect = "Allow"

    actions = [
      "iam:CreatePolicy",
      "iam:DeletePolicy",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:TagPolicy",
      "iam:UntagPolicy"
    ]

    resources = [
      "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:policy/${var.project_name}-*"
    ]
  }

  statement {
    sid    = "CreateRequiredServiceLinkedRoles"
    effect = "Allow"

    actions = [
      "iam:CreateServiceLinkedRole"
    ]

    resources = [
      "*"
    ]

    condition {
      test     = "StringLike"
      variable = "iam:AWSServiceName"

      values = [
        "ecs.amazonaws.com",
        "ecs.application-autoscaling.amazonaws.com",
        "elasticloadbalancing.amazonaws.com",
        "rds.amazonaws.com"
      ]
    }
  }
}

resource "aws_iam_role_policy" "github_apply_iam" {
  name = "project-iam"

  role = aws_iam_role.github_terraform_apply.id

  policy = data.aws_iam_policy_document.github_apply_iam.json
}

############ Apply role remote-state permissions ############
data "aws_iam_policy_document" "github_apply_state" {
  statement {
    effect = "Allow"

    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket"
    ]

    resources = [
      aws_s3_bucket.terraform_state.arn
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "s3:GetObject",
      "s3:PutObject"
    ]

    resources = [
      "${aws_s3_bucket.terraform_state.arn}/prod/terraform.tfstate"
    ]
  }

  statement {
    effect = "Allow"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]

    resources = [
      "${aws_s3_bucket.terraform_state.arn}/prod/terraform.tfstate.tflock"
    ]
  }
}

resource "aws_iam_role_policy" "github_apply_state" {
  name = "terraform-state"

  role = aws_iam_role.github_terraform_apply.id

  policy = data.aws_iam_policy_document.github_apply_state.json
}

# DEV
locals {
  github_main_subject = "repo:${var.github_owner}/${var.github_repository}:ref:refs/heads/main"
  github_dev_subjects = [
    "repo:${var.github_owner}/${var.github_repository}:ref:refs/heads/dev",
    "repo:${var.github_owner}@*/${var.github_repository}@*:ref:refs/heads/dev"
  ]
}

data "aws_iam_policy_document" "github_dev_deploy_assume_role" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRoleWithWebIdentity"
    ]

    principals {
      type = "Federated"

      identifiers = [
        aws_iam_openid_connect_provider.github.arn
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"

      values = [
        "sts.amazonaws.com"
      ]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"

      values = local.github_dev_subjects
    }
  }
}

resource "aws_iam_role" "github_dev_deploy" {
  name = "${var.project_name}-github-dev-deploy"

  assume_role_policy = data.aws_iam_policy_document.github_dev_deploy_assume_role.json
}

resource "aws_iam_role" "github_dev_deploy" {
  name = "${var.project_name}-github-dev-deploy"

  assume_role_policy = data.aws_iam_policy_document.github_dev_deploy_assume_role.json
}

############ Github push the Docker Images ############
data "aws_iam_policy_document" "github_dev_deploy" {
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
    sid    = "PushApplicationImages"
    effect = "Allow"

    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:DescribeImages"
    ]

    resources = [
      "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${var.project_name}-transport-service",
      "arn:${data.aws_partition.current.partition}:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${var.project_name}-audit-consumer"
    ]
  }

  statement {
    sid    = "UseRunCommand"
    effect = "Allow"

    actions = [
      "ssm:SendCommand"
    ]

    resources = [
      "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}::document/AWS-RunShellScript"
    ]
  }

  statement {
    sid    = "DeployOnlyToDev"
    effect = "Allow"

    actions = [
      "ssm:SendCommand"
    ]

    resources = [
      "arn:${data.aws_partition.current.partition}:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/*"
    ]

    condition {
      test     = "StringLike"
      variable = "ssm:resourceTag/Environment"

      values = [
        "dev"
      ]
    }
  }

  statement {
    sid    = "ReadRunCommand"
    effect = "Allow"

    actions = [
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
      "ssm:ListCommands"
    ]

    resources = [
      "*"
    ]
  }

  statement {
    sid    = "FindDevInstance"
    effect = "Allow"

    actions = [
      "ec2:DescribeInstances"
    ]

    resources = [
      "*"
    ]
  }
}

resource "aws_iam_role_policy" "github_dev_deploy" {
  name = "dev-deployment"
  role = aws_iam_role.github_dev_deploy.id

  policy = data.aws_iam_policy_document.github_dev_deploy.json
}