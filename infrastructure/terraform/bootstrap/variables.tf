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

############ Github OIDC ############
variable "github_owner" {
  description = "GitHub repository owner."
  type        = string
  default     = "khanhdoan-2809"
}

variable "github_repository" {
  description = "GitHub repository name."
  type        = string
  default     = "cdc-kafka"
}

variable "github_environment" {
  description = "GitHub environment allowed to deploy production infrastructure."
  type        = string
  default     = "production"
}
