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
  value       = aws_nat_gateway.main.id
}

output "nat_public_ip" {
  description = "Public Elastic IP used by the NAT Gateway."
  value       = aws_eip.nat.public_ip
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

############ ALB & ECS Outputs ############
output "alb_dns_name" {
  description = "Public DNS name of the transport-service ALB."
  value       = aws_lb.transport.dns_name
}

output "alb_url" {
  description = "HTTP URL for the transport-service ALB."
  value       = "http://${aws_lb.transport.dns_name}"
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.main.name
}

output "transport_ecs_service_name" {
  description = "transport-service ECS service name."
  value       = aws_ecs_service.transport.name
}

output "transport_task_definition_family" {
  description = "transport-service task definition family."
  value       = aws_ecs_task_definition.transport.family
}

output "transport_ecs_security_group_id" {
  description = "Security group used by transport-service ECS tasks."
  value       = aws_security_group.transport_ecs.id
}

output "alb_security_group_id" {
  description = "Security group used by the public ALB."
  value       = aws_security_group.alb.id
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

output "db_bootstrap_task_definition_arn" {
  description = "ECS task definition used for one-off database bootstrap."
  value       = aws_ecs_task_definition.db_bootstrap.arn
}

output "db_bootstrap_security_group_id" {
  description = "Security group used by the database bootstrap task."
  value       = aws_security_group.db_bootstrap.id
}

output "db_bootstrap_log_group_name" {
  description = "CloudWatch log group for database bootstrap."
  value       = aws_cloudwatch_log_group.db_bootstrap.name
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