output "aws_account_id" {
  value = data.aws_caller_identity.current.account_id
}

output "state_bucket_name" {
  value = aws_s3_bucket.terraform_state.bucket
}

output "aws_region" {
  value = var.aws_region
}

############ GitHub OIDC ############

output "github_terraform_plan_role_arn" {
  description = "GitHub Actions role used for Terraform plans."
  value       = aws_iam_role.github_terraform_plan.arn
}

output "github_terraform_apply_role_arn" {
  description = "GitHub Actions role used for production Terraform apply."
  value       = aws_iam_role.github_terraform_apply.arn
}

output "github_terraform_dev_apply_role_arn" {
  description = "GitHub Actions role used for DEV Terraform apply."
  value       = aws_iam_role.github_terraform_dev_apply.arn
}

############ Deployment ############

output "github_deploy_role_arn" {
  description = "GitHub Actions role used to deploy production application releases."
  value       = aws_iam_role.github_deploy.arn
}

output "github_dev_deploy_role_arn" {
  description = "GitHub Actions role used to deploy DEV application images."
  value       = aws_iam_role.github_dev_deploy.arn
}