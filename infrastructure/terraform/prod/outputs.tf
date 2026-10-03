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