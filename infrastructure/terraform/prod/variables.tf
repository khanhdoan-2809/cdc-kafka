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
  description = "Initial PostgreSQL database."
  type        = string
  default     = "transport"
}

variable "db_master_username" {
  description = "RDS administrator username."
  type        = string
  default     = "cdc_admin"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
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

############ ECS ############
variable "transport_container_port" {
  description = "Port exposed by transport-service."
  type        = number
  default     = 8080
}

variable "transport_task_cpu" {
  description = "Fargate CPU units for transport-service."
  type        = number
  default     = 512
}

variable "transport_task_memory" {
  description = "Fargate memory in MiB for transport-service."
  type        = number
  default     = 1024
}

variable "transport_desired_count" {
  description = "Number of transport-service ECS tasks."
  type        = number
  default     = 0
}