############ Network Outputs ############

output "vpc_id" {
  description = "Production VPC ID."
  value       = aws_vpc.main.id
}

output "availability_zones" {
  description = "Availability Zones used by the VPC."
  value       = local.availability_zones
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = aws_subnet.public[*].id
}

output "app_subnet_ids" {
  description = "Private application subnet IDs."
  value       = aws_subnet.app[*].id
}

output "database_subnet_ids" {
  description = "Private database subnet IDs."
  value       = aws_subnet.database[*].id
}

output "nat_gateway_id" {
  description = "NAT Gateway used by application subnets."
  value       = try(aws_nat_gateway.main[0].id, null)
}

output "nat_public_ip" {
  description = "Public Elastic IP used by the NAT Gateway."
  value       = try(aws_eip.nat[0].public_ip, null)
}

############ ECR Outputs ############
output "transport_service_ecr_repository_url" {
  description = "ECR repository URL for transport-service."
  value       = aws_ecr_repository.app["transport_service"].repository_url
}

output "audit_consumer_ecr_repository_url" {
  description = "ECR repository URL for audit-consumer."
  value       = aws_ecr_repository.app["audit_consumer"].repository_url
}

output "ecr_repository_urls" {
  description = "All application ECR repository URLs."

  value = {
    for key, repository in aws_ecr_repository.app :
    key => repository.repository_url
  }
}

############ RDS Outputs ############
output "rds_endpoint" {
  description = "PostgreSQL RDS endpoint."
  value       = aws_db_instance.postgres.address
}

output "rds_port" {
  description = "PostgreSQL RDS port."
  value       = aws_db_instance.postgres.port
}

output "rds_database_name" {
  description = "Initial PostgreSQL database name."
  value       = aws_db_instance.postgres.db_name
}

output "rds_security_group_id" {
  description = "RDS security group ID."
  value       = aws_security_group.rds.id
}

output "rds_master_secret_arn" {
  description = "Secrets Manager secret containing the RDS administrator credentials."

  value = try(
    aws_db_instance.postgres.master_user_secret[0].secret_arn,
    null
  )

  sensitive = true
}

############ Application EC2 Outputs ############

output "app_instance_id" {
  description = "Production application EC2 instance ID (use AWS SSM)."
  value       = module.app_ec2.instance_id
}

output "app_public_ip" {
  description = "Public IP of the app host. No public ingress is enabled yet."
  value       = module.app_ec2.public_ip
}

############ Database Bootstrap Outputs ############

output "transport_db_secret_arn" {
  description = "Secrets Manager secret for transport-service database credentials."
  value       = aws_secretsmanager_secret.transport_db_app.arn
}

output "audit_db_secret_arn" {
  description = "Secrets Manager secret for audit-consumer database credentials."
  value       = aws_secretsmanager_secret.audit_db_app.arn
}

output "debezium_db_secret_arn" {
  description = "Secrets Manager secret for Debezium database credentials."
  value       = aws_secretsmanager_secret.debezium_db.arn
}

############ MSK Outputs ############

output "msk_cluster_arn" {
  description = "ARN of the production MSK Serverless cluster."
  value       = aws_msk_serverless_cluster.main.arn
}

output "msk_bootstrap_brokers" {
  description = "IAM bootstrap brokers for the production MSK Serverless cluster."
  value       = aws_msk_serverless_cluster.main.bootstrap_brokers_sasl_iam
}

output "msk_security_group_id" {
  description = "Security group attached to the MSK Serverless cluster."
  value       = aws_security_group.msk.id
}

output "msk_client_security_group_id" {
  description = "Security group to attach to applications that connect to MSK."
  value       = aws_security_group.msk_client.id
}
