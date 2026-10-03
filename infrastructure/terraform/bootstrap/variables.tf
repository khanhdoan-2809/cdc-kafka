variable "project_name" {
  description = "Project name used for AWS resource naming."
  type        = string
  default     = "cdc-kafka"
}

variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "ap-southeast-1"
}