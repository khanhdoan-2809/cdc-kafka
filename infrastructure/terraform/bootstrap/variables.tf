variable "project_name" {
  description = "Project name for AWS resource names."
  type        = string
  default     = "cdc-kafka"
}

variable "aws_region" {
  description = "AWS Region used by this environment."
  type        = string
  default     = "ap-southeast-1"
}

variable "aws_account_id" {
  description = "Expected AWS account ID."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "environment" {
  description = "Target environment: dev or prod."
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be either dev or prod."
  }
}

variable "github_owner" {
  type    = string
  default = "khanhdoan-2809"
}

variable "github_repository" {
  type    = string
  default = "cdc-kafka"
}

variable "github_environment" {
  description = "Protected GitHub environment for production."
  type        = string
  default     = "production"
}

variable "github_oidc_subject_prefix" {
  description = "Exact GitHub OIDC subject prefix."
  type        = string
}
