GitHub CI/CD Notes

Repository Variables

Go to:

GitHub Repository → Settings → Secrets and variables → Actions → Variables

Add:

AWS_ACCOUNT_ID = <your-aws-account-id>
AWS_REGION     = ap-southeast-1

Repository Secrets

None required for the current setup.

Do not add:

AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_SESSION_TOKEN

GitHub Actions uses AWS OIDC and temporary credentials.

DEV Credentials

Do not store these in GitHub:

TRANSPORT_DB_PASSWORD
AUDIT_DB_PASSWORD
GRAFANA_ADMIN_PASSWORD

They stay on the DEV EC2 in:

/opt/cdc-kafka/.env.dev

AWS Role Mapping

PR
→ cdc-kafka-github-terraform-plan

main
→ cdc-kafka-github-terraform-apply

dev
→ cdc-kafka-github-dev-deploy

production release
→ cdc-kafka-github-deploy

Current Recommendation

GitHub Actions
├── Variables
│   ├── AWS_ACCOUNT_ID
│   └── AWS_REGION
└── Secrets
└── none