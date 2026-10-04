output "dev_instance_id" {
  description = "Development EC2 instance ID."
  value       = aws_instance.dev.id
}

output "dev_public_ip" {
  description = "Development EC2 public IPv4 address."
  value       = aws_instance.dev.public_ip
}

output "dev_public_dns" {
  description = "Development EC2 public DNS."
  value       = aws_instance.dev.public_dns
}

output "dev_security_group_id" {
  description = "Development EC2 security group."
  value       = aws_security_group.dev.id
}

output "dev_ssm_command" {
  description = "Command used to open an administrative SSM session."
  value       = "aws ssm start-session --target ${aws_instance.dev.id} --region ${var.aws_region}"
}

output "dev_transport_database" {
  description = "Transport PostgreSQL connection information."

  value = {
    host     = aws_instance.dev.public_dns
    port     = 5432
    database = "transport"
    username = "transport"
  }
}

output "dev_audit_database" {
  description = "Audit PostgreSQL connection information."

  value = {
    host     = aws_instance.dev.public_dns
    port     = 5433
    database = "audit"
    username = "audit"
  }
}

output "dev_transport_url" {
  description = "DEV transport-service URL."
  value       = "http://${aws_instance.dev.public_dns}:8080"
}

output "dev_kafka_ui_url" {
  description = "DEV Kafka UI URL."
  value       = "http://${aws_instance.dev.public_dns}:8088"
}

output "dev_grafana_url" {
  description = "DEV Grafana URL."
  value       = "http://${aws_instance.dev.public_dns}:3000"
}

output "dev_kafka_bootstrap_server" {
  description = "DEV Kafka external bootstrap server. Available after Phase 23.11."
  value       = "${aws_instance.dev.public_dns}:9092"
}