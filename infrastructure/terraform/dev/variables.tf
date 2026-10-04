variable "project_name" {
  description = "Project name."
  type        = string
  default     = "cdc-kafka"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "dev"
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

variable "vpc_cidr" {
  description = "CIDR of the development VPC."
  type        = string
  default     = "10.30.0.0/16"
}

variable "dev_instance_type" {
  description = "EC2 instance type used by the development Docker stack."
  type        = string
  default     = "t3.medium"
}

variable "dev_root_volume_size" {
  description = "Root EBS volume size in GiB."
  type        = number
  default     = 50
}