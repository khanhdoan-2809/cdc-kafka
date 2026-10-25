variable "project_name" {
  description = "Project name."
  type        = string
  default     = "cdc-kafka"
}

variable "environment" {
  description = "Deployment environment."
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "ap-southeast-1"
}

variable "aws_account_id" {
  description = "Expected AWS account ID."
  type        = string
}

############ Network ############

variable "vpc_cidr" {
  description = "CIDR block used by the production VPC."
  type        = string
  default     = "10.20.0.0/16"
}

############ Database ############

variable "db_name" {
  description = "Transport PostgreSQL database."
  type        = string
  default     = "transport"
}

variable "audit_db_name" {
  description = "Audit PostgreSQL database."
  type        = string
  default     = "audit"
}

variable "db_master_username" {
  description = "RDS administrator username."
  type        = string
  default     = "cdc_admin"
}

variable "db_engine_version" {
  description = "PostgreSQL engine version."
  type        = string
  default     = "17.11"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.small"
}

variable "db_allocated_storage" {
  description = "Initial RDS storage in GiB."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Maximum storage autoscaling limit in GiB."
  type        = number
  default     = 100
}

variable "db_multi_az" {
  description = "Whether RDS uses a Multi-AZ deployment."
  type        = bool
  default     = false
}

############ Application EC2 ############

variable "app_instance_type" {
  description = "Production Docker host; larger than the DEV instance."
  type        = string
  default     = "t3a.large"
}

variable "app_root_volume_size" {
  description = "Encrypted gp3 root volume size in GiB."
  type        = number
  default     = 30
}

variable "enable_msk_connect" {
  description = "Enable only after database roles, schema and Debezium plugin are ready."
  type        = bool
  default     = false
}
