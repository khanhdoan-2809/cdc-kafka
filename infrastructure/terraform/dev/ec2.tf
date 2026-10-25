module "app_ec2" {
  source = "../modules/app-ec2"

  project_name     = var.project_name
  environment      = var.environment
  aws_region       = var.aws_region
  aws_account_id   = var.aws_account_id
  instance_type    = var.dev_instance_type
  root_volume_size = var.dev_root_volume_size

  subnet_id          = aws_subnet.public.id
  security_group_ids = [aws_security_group.dev.id]
}
