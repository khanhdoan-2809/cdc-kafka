output "aws_account_id" {
  value = data.aws_caller_identity.current.account_id
}

output "environment" {
  value = var.environment
}

output "aws_region" {
  value = var.aws_region
}

output "state_bucket_name" {
  value = aws_s3_bucket.terraform_state.bucket
}

output "github_terraform_plan_role_arn" {
  value = aws_iam_role.github_terraform_plan.arn
}

output "github_terraform_dev_apply_role_arn" {
  value = local.is_dev ? aws_iam_role.github_terraform_dev_apply[0].arn : null
}

output "github_dev_deploy_role_arn" {
  value = local.is_dev ? aws_iam_role.github_dev_deploy[0].arn : null
}

output "github_terraform_apply_role_arn" {
  value = local.is_prod ? aws_iam_role.github_terraform_apply[0].arn : null
}

output "github_deploy_role_arn" {
  value = local.is_prod ? aws_iam_role.github_deploy[0].arn : null
}