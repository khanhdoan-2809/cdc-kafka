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

variable "vpc_cidr" {
  description = "CIDR block used by the production VPC."
  type        = string
  default     = "10.20.0.0/16"
}