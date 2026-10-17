# Terraform Bootstrap — Quick Summary

## What is it?

`infrastructure/terraform/bootstrap` prepares AWS for GitHub Actions CI/CD.

```text
Developer runs Bootstrap (first time)
                 |
                 v
         Terraform Bootstrap
           |     |     |
           v     v     v
          S3   OIDC  IAM roles
           |           |
           +-----+-----+
                 v
           GitHub Actions
                 |
                 v
      Terraform DEV + Docker deploy
```

- **S3:** Stores Terraform state for DEV.
- **OIDC:** Lets GitHub authenticate to AWS without permanent AWS keys.
- **IAM roles:** Control what GitHub can do in AWS.

## Important files

| File | Purpose |
|---|---|
| `main.tf` | Creates the S3 state bucket. |
| `github.tf` | Creates GitHub OIDC and IAM roles/policies. |
| `variables.tf` / `terraform.tfvars` | Configure AWS account, region, and environment. |
| `outputs.tf` | Shows S3 bucket name and IAM role ARNs. |

**Bootstrap** prepares AWS permissions and state storage. **Terraform DEV** creates EC2, ECR, and networking. **Deploy DEV** builds and runs your applications.

## 1. Run Bootstrap locally (first time)

Run these commands from the **project root**. You need AWS CLI credentials with permission to create S3 and IAM resources, but **you do not need to set `AWS_PROFILE`** if your default credentials already work.

```bash
# Confirm your AWS login and account
aws sts get-caller-identity

# Supply the required Terraform account ID
export TF_VAR_aws_account_id="$(aws sts get-caller-identity --query Account --output text)"

# Create the Bootstrap resources
terraform -chdir=infrastructure/terraform/bootstrap init
terraform -chdir=infrastructure/terraform/bootstrap plan
terraform -chdir=infrastructure/terraform/bootstrap apply
terraform -chdir=infrastructure/terraform/bootstrap output
```

If `aws sts get-caller-identity` fails, configure AWS credentials first (for example, with `aws configure` or AWS IAM Identity Center login).

**Optional — only if you use a named AWS CLI profile:**

```bash
export AWS_PROFILE=dev
```

Run this *before* the commands above. On Windows PowerShell, use `$env:AWS_PROFILE = "dev"` instead.

Your `terraform.tfvars` currently selects `environment = "dev"`.

**Important:** Bootstrap stores its own state locally (`terraform.tfstate`). Back it up securely and do not commit it to Git.

## 2. GitHub Actions variables for DEV

Go to **GitHub repository → Settings → Secrets and variables → Actions → Variables → New repository variable**.

Create these **7 repository variables** (used by `.github/workflows/terraform-dev.yml` and `deploy-dev.yml`):

| Variable | Value / where to get it |
|---|---|
| `AWS_REGION` | `ap-southeast-1` (your configured AWS region) |
| `DEV_AWS_ACCOUNT_ID` | Your 12-digit AWS account ID |
| `DEV_DEVELOPER_CIDR` | Your allowed public IP range, e.g. `203.0.113.10/32` (example only) |
| `DEV_TF_STATE_BUCKET` | Bootstrap output `state_bucket_name` |
| `DEV_TERRAFORM_PLAN_ROLE_ARN` | Bootstrap output `github_terraform_plan_role_arn` |
| `DEV_TERRAFORM_APPLY_ROLE_ARN` | Bootstrap output `github_terraform_dev_apply_role_arn` |
| `DEV_DEPLOY_ROLE_ARN` | Bootstrap output `github_dev_deploy_role_arn` |

Get the outputs with:

```bash
terraform -chdir=infrastructure/terraform/bootstrap output -raw state_bucket_name
terraform -chdir=infrastructure/terraform/bootstrap output -raw github_terraform_plan_role_arn
terraform -chdir=infrastructure/terraform/bootstrap output -raw github_terraform_dev_apply_role_arn
terraform -chdir=infrastructure/terraform/bootstrap output -raw github_dev_deploy_role_arn
```

### Do I need GitHub Secrets or Environments?

- **GitHub Secrets:** **No** for the current DEV workflows. They use GitHub OIDC and IAM roles, not AWS access keys.
- **GitHub Environment:** **No** for DEV as currently written. The Terraform value `environment = "dev"` is not a GitHub Environment setting.
- **Local Bootstrap:** Needs valid AWS credentials and the required Terraform variable `aws_account_id`. `AWS_PROFILE` is optional; none of these need to be stored as GitHub Actions secrets.

## 3. What happens in CI/CD?

```text
Pull request to dev → Terraform validate / plan
Push to dev         → Maven verify → Terraform DEV apply
                    → Build images → ECR → EC2 via SSM → Smoke test
```

## Before the first deployment

- Fix `github_dev_deploy` in `github.tf`: add `ecr:GetAuthorizationToken` permission so ECR login works.
- Fix the Terraform plan policy: `s3:ListBucket` must use the **S3 bucket ARN**, not an object ARN.
- Don't switch Bootstrap from `dev` to `prod` using the same local state without planning the state separation.