
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

  cidr_ipv4    = var.developer_cidr
  ip_protocol = "tcp"
  from_port   = each.value.port
  to_port     = each.value.port

  description = "Developer access to ${each.value.description}"
}
