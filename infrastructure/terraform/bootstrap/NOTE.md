# Github OIDC
## Create GitHub production environment
In GitHub:

Repository
→ Settings
→ Environments
→ New environment
→ production

--->
flow becomes:
merge main
↓
terraform apply job
↓
production environment
↓
approval
↓
AWS

## GitHub repository variables
Settings
→ Secrets and variables
→ Actions
→ Variables
Create:
AWS_REGION
value:
ap-southeast-1
Create:
AWS_ACCOUNT_ID
Create:
TF_STATE_BUCKET
with:
terraform -chdir=infrastructure/terraform/bootstrap output -raw state_bucket_name
Create:
AWS_TERRAFORM_PLAN_ROLE_ARN
using:
terraform -chdir=infrastructure/terraform/bootstrap \
output -raw github_terraform_plan_role_arn
Create:
AWS_TERRAFORM_APPLY_ROLE_ARN
using:
terraform -chdir=infrastructure/terraform/bootstrap \
output -raw github_terraform_apply_role_arn
No AWS secrets are required.