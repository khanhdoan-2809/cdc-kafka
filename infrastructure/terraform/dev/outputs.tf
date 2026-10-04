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