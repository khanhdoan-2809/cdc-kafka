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