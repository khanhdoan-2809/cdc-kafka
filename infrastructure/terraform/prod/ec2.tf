# One small Docker host for both application containers.
# No public application ingress is opened in Phase 3; use SSM to administer it.
resource "aws_security_group" "app_ec2" {
  name        = "${var.project_name}-${var.environment}-app-ec2"
  description = "Production Docker application host"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-app-ec2"
  }
}

resource "aws_vpc_security_group_egress_rule" "app_ec2_outbound" {
  security_group_id = aws_security_group.app_ec2.id
  description       = "Allow outbound for image pulls, AWS APIs, RDS and MSK"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

module "app_ec2" {
  source = "../modules/app-ec2"

  project_name     = var.project_name
  environment      = var.environment
  aws_region       = var.aws_region
  aws_account_id   = var.aws_account_id
  instance_type    = var.app_instance_type
  root_volume_size = var.app_root_volume_size

  subnet_id          = aws_subnet.public[0].id
  security_group_ids = [aws_security_group.app_ec2.id]
}
