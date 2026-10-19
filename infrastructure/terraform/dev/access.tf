
locals {
  developer_ports = {
    transport_api = {
      port        = 8080
      description = "Transport API"
    }

    kafka_ui = {
      port        = 8088
      description = "Kafka UI"
    }

    grafana = {
      port        = 3000
      description = "Grafana"
    }
  }
}

resource "aws_vpc_security_group_ingress_rule" "developer" {
  for_each = local.developer_ports

  security_group_id = aws_security_group.dev.id

  cidr_ipv4   = var.developer_cidr
  ip_protocol = "tcp"
  from_port   = each.value.port
  to_port     = each.value.port

  description = "Developer access to ${each.value.description}"
}

# Browser-based SSH from the AWS EC2 console (EC2 Instance Connect).
# This is an AWS-managed regional IP range, not 0.0.0.0/0 or the developer's IP.
# Amazon Linux 2023 supports EC2 Instance Connect and can use a temporary SSH key.
data "aws_ec2_managed_prefix_list" "ec2_instance_connect" {
  name = "com.amazonaws.${var.aws_region}.ec2-instance-connect"
}

resource "aws_vpc_security_group_ingress_rule" "ec2_instance_connect_ssh" {
  security_group_id = aws_security_group.dev.id

  ip_protocol    = "tcp"
  from_port      = 22
  to_port        = 22
  prefix_list_id = data.aws_ec2_managed_prefix_list.ec2_instance_connect.id

  description = "SSH from AWS EC2 Instance Connect in this region"
}