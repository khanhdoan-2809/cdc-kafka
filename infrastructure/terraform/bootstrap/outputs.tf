output "aws_account_id" {
  value = data.aws_caller_identity.current.account_id
}

output "state_bucket_name" {
  value = aws_s3_bucket.terraform_state.bucket
}

output "aws_region" {
  value = var.aws_region
}

############ Github OIDC ############
output "github_terraform_plan_role_arn" {
  description = "GitHub Actions role used for Terraform plans."
  value       = aws_iam_role.github_terraform_plan.arn
}

output "github_terraform_apply_role_arn" {
  description = "GitHub Actions role used for Terraform apply."
  value       = aws_iam_role.github_terraform_apply.arn
}

############ Deployment ############
output "github_deploy_role_arn" {
  description = "GitHub Actions role used to deploy application releases."
  value       = aws_iam_role.github_deploy.arn
}