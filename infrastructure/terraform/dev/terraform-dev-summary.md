# Terraform DEV — Quick Summary

## What is it?

`infrastructure/terraform/dev` creates the AWS infrastructure for the **development environment**.

```text
Terraform Bootstrap (S3 state + IAM roles)
                |
                v
           Terraform DEV
           /          \
      EC2 server     2 ECR repositories
           |
           v
       Docker Compose
 (Spring Boot, PostgreSQL, Kafka,
  Debezium, Kafka UI, Grafana, etc.)
```

**Bootstrap** sets up AWS permissions and Terraform state. **DEV** creates the server, network, and image repositories. **Deploy DEV** runs the containers.

## What does it create?

| Resource | Purpose |
|---|---|
| VPC, public subnet, internet gateway | Networking for the development server. |
| EC2 | One Amazon Linux 2023 server, default `t3a.medium`, 50 GiB disk. |
| Security group | Allows your IP (`developer_cidr`) to access API **8080**, Kafka UI **8088**, Grafana **3000**. |
| IAM role + SSM | EC2 can pull ECR images and receive deployment commands without SSH. |
| ECR | Two Docker image repositories: transport service and audit consumer. |
| S3 backend | Stores DEV Terraform state in the bucket created by Bootstrap. |

## Important files

| File | Purpose |
|---|---|
| `main.tf` | EC2, networking, IAM role, and startup scripts. |
| `access.tf` | Developer access rules. |
| `ecr.tf` | Docker image repositories. |
| `backend.tf` | Remote Terraform state in S3. |
| `variables.tf` | Settings such as region, instance type, and IP range. |
| `outputs.tf` | EC2 details, service URLs, and SSM commands. |

## GitHub Actions configuration

Go to **Repository → Settings → Secrets and variables → Actions → Variables**.

| Repository variable | Value |
|---|---|
| `AWS_REGION` | AWS region, e.g. `ap-southeast-1` |
| `DEV_AWS_ACCOUNT_ID` | Your AWS account ID |
| `DEV_DEVELOPER_CIDR` | Your public IP range, e.g. `YOUR_IP/32` |
| `DEV_TF_STATE_BUCKET` | Bootstrap output: `state_bucket_name` |
| `DEV_TERRAFORM_PLAN_ROLE_ARN` | Bootstrap plan role ARN |
| `DEV_TERRAFORM_APPLY_ROLE_ARN` | Bootstrap DEV apply role ARN |
| `DEV_DEPLOY_ROLE_ARN` | Bootstrap DEV deployment role ARN |

**GitHub Secrets:** None required by the current DEV workflows. GitHub authenticates to AWS using **OIDC**, not permanent AWS access keys.

## How CI/CD works

```text
Pull request to dev → Terraform validate + plan

Push to dev → Maven verify
            → Terraform DEV apply
            → Build images → Push to ECR
            → SSM deploy to EC2 (Docker Compose)
            → Smoke test
```

Workflows: `.github/workflows/terraform-dev.yml` and `.github/workflows/deploy-dev.yml`.

## Run manually (optional)

Run **Bootstrap first**. With valid AWS credentials, from the project root:

```bash
aws sts get-caller-identity

export TF_VAR_aws_account_id="$(aws sts get-caller-identity --query Account --output text)"
export TF_VAR_developer_cidr="YOUR_IP/32"

terraform -chdir=infrastructure/terraform/dev init \
  -backend-config="bucket=YOUR_BOOTSTRAP_STATE_BUCKET" \
  -backend-config="key=dev/terraform.tfstate" \
  -backend-config="region=ap-southeast-1" \
  -backend-config="encrypt=true" \
  -backend-config="use_lockfile=true"

terraform -chdir=infrastructure/terraform/dev plan
terraform -chdir=infrastructure/terraform/dev apply
terraform -chdir=infrastructure/terraform/dev output
```

`AWS_PROFILE` is **optional** if your default AWS credentials work. Replace the example IP and bucket. Change the region if necessary.

**In short:** Bootstrap → Terraform DEV → Build Docker images → ECR → EC2 + Docker Compose.
