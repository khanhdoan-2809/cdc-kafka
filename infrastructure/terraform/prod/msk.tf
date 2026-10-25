############ MSK Security Groups ############

resource "aws_security_group" "msk" {
  name        = "${var.project_name}-${var.environment}-msk"
  description = "Security group for MSK Serverless"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-msk-sg"
  }
}

resource "aws_security_group" "msk_client" {
  name        = "${var.project_name}-${var.environment}-msk-client"
  description = "Security group for applications connecting to MSK"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-msk-client-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "msk_from_clients" {
  security_group_id = aws_security_group.msk.id

  referenced_security_group_id = aws_security_group.msk_client.id

  description = "Allow IAM authenticated Kafka clients"

  ip_protocol = "tcp"
  from_port   = 9098
  to_port     = 9098
}

resource "aws_vpc_security_group_ingress_rule" "msk_from_app_ec2" {
  security_group_id            = aws_security_group.msk.id
  referenced_security_group_id = aws_security_group.app_ec2.id
  description                  = "Allow production Docker host to connect over IAM TLS"
  ip_protocol                  = "tcp"
  from_port                    = 9098
  to_port                      = 9098
}

resource "aws_vpc_security_group_egress_rule" "msk_outbound" {
  security_group_id = aws_security_group.msk.id

  ip_protocol = "-1"
  cidr_ipv4   = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "msk_client_outbound" {
  security_group_id = aws_security_group.msk_client.id

  ip_protocol = "-1"
  cidr_ipv4   = "0.0.0.0/0"
}

############ MSK Serverless ############

resource "aws_msk_serverless_cluster" "main" {
  cluster_name = "${var.project_name}-${var.environment}"

  vpc_config {
    subnet_ids = aws_subnet.app[*].id

    security_group_ids = [
      aws_security_group.msk.id
    ]
  }

  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-msk"
  }
}