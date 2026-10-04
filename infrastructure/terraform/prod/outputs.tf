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
