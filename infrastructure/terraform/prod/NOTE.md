# PROD infrastructure — Phase 3

Uses a separate production AWS account and state bucket.

Infrastructure: one EC2 Docker host, private PostgreSQL RDS, MSK Serverless,
ECR and an optional MSK Connect connector. A NAT gateway is created only when
`enable_msk_connect=true` to support private MSK Connect workers. This adds
a fixed monthly cost after CDC is enabled.

1. Configure `terraform.tfvars` using `terraform.tfvars.example` in the PROD account.
2. Initialize the existing PROD S3 backend with the correct `PROD_TF_STATE_BUCKET`.
3. Run `terraform fmt -check -recursive`, `terraform validate` and `terraform plan`.
4. Review and approve a manual `Terraform PROD` workflow dispatch on `main`.

Do not deploy the applications yet. No HTTP/HTTPS ingress is open on the host.
Database secret values, DB bootstrap, production Docker Compose and GitHub
release deployment are in subsequent phases.

**The old ECS release and database bootstrap workflows were removed**, as they
would no longer work for the EC2 design. New workflows are implemented later.
