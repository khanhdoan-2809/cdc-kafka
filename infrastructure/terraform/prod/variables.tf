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