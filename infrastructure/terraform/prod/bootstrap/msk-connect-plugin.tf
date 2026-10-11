locals {
  msk_connect_plugin_bucket_name = "${var.project_name}-${data.aws_caller_identity.current.account_id}-${var.aws_region}-msk-connect-plugins"
}

resource "aws_s3_bucket" "msk_connect_plugins" {
  bucket = local.msk_connect_plugin_bucket_name

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Project   = var.project_name
    ManagedBy = "Terraform"
    Purpose   = "MSKConnectPlugins"
  }
}

resource "aws_s3_bucket_versioning" "msk_connect_plugins" {
  bucket = aws_s3_bucket.msk_connect_plugins.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "msk_connect_plugins" {
  bucket = aws_s3_bucket.msk_connect_plugins.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "msk_connect_plugins" {
  bucket = aws_s3_bucket.msk_connect_plugins.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}