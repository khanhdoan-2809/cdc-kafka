
output "dev_instance_id" {
  description = "DEV EC2 instance ID."
  value       = aws_instance.dev.id
}

output "dev_public_ip" {
  description = "DEV EC2 public IP."
  value       = aws_instance.dev.public_ip
}

output "dev_public_dns" {
  description = "DEV EC2 public DNS."
  value       = aws_instance.dev.public_dns
}

output "dev_security_group_id" {
  description = "DEV EC2 security group."
  value       = aws_security_group.dev.id
}

output "dev_ssm_command" {
  description = "Connect to DEV EC2 using SSM."
  value       = "aws ssm start-session --target ${aws_instance.dev.id} --region ${var.aws_region}"
}

output "dev_transport_db_tunnel" {
  description = "Forward transport PostgreSQL to localhost:5432."
  value       = "aws ssm start-session --target ${aws_instance.dev.id} --region ${var.aws_region} --document-name AWS-StartPortForwardingSession --parameters 'portNumber=5432,localPortNumber=5432'"
}

output "dev_audit_db_tunnel" {
  description = "Forward audit PostgreSQL to localhost:5433."
  value       = "aws ssm start-session --target ${aws_instance.dev.id} --region ${var.aws_region} --document-name AWS-StartPortForwardingSession --parameters 'portNumber=5433,localPortNumber=5433'"
}

output "dev_transport_database" {
  description = "Transport database connection via SSM tunnel."

  value = {
    host     = "127.0.0.1"
    port     = 5432
    database = "transport"
    username = "transport"
  }
}

output "dev_audit_database" {
  description = "Audit database connection via SSM tunnel."

  value = {
    host     = "127.0.0.1"
    port     = 5433
    database = "audit"
    username = "audit"
  }
}

output "dev_transport_url" {
  description = "Transport API URL."
  value       = "http://${aws_instance.dev.public_dns}:8080"
}

output "dev_kafka_ui_url" {
  description = "Kafka UI URL."
  value       = "http://${aws_instance.dev.public_dns}:8088"
}

output "dev_grafana_url" {
  description = "Grafana URL."
  value       = "http://${aws_instance.dev.public_dns}:3000"
}
