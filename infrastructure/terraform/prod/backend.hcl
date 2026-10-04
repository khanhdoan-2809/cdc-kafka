bucket       = "cdc-kafka-terraform-state-REPLACE_ME"
key          = "prod/terraform.tfstate"
region       = "ap-southeast-1"
encrypt      = true
use_lockfile = true # No DynamoDB table is necessary anymore.