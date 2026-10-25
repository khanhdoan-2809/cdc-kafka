############ PostgreSQL Parameter Group ############

resource "aws_db_parameter_group" "postgres" {
  name   = "${var.project_name}-${var.environment}-postgres17"
  family = "postgres17"

  parameter {
    name         = "rds.logical_replication"
    value        = "1"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "max_replication_slots"
    value        = "4"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "max_wal_senders"
    value        = "4"
    apply_method = "pending-reboot"
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-postgres17"
  }
}

############ Database Subnet Group ############

resource "aws_db_subnet_group" "main" {
  name = "${var.project_name}-${var.environment}"

  subnet_ids = aws_subnet.database[*].id

  tags = {
    Name = "${var.project_name}-${var.environment}-db-subnet-group"
  }
}

############ RDS Security Group ############

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-${var.environment}-rds"
  description = "Security group for PostgreSQL RDS"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-rds-sg"
  }
}

############ PostgreSQL RDS ############

resource "aws_db_instance" "postgres" {
  identifier = "${var.project_name}-${var.environment}-postgres"

  engine         = "postgres"
  engine_version = var.db_engine_version

  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_master_username

  # Password is generated and managed by RDS in Secrets Manager.
  # Terraform never receives the plaintext master password.
  manage_master_user_password = true

  port = 5432

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage

  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name = aws_db_subnet_group.main.name
  parameter_group_name = aws_db_parameter_group.postgres.name

  vpc_security_group_ids = [
    aws_security_group.rds.id
  ]

  publicly_accessible = false

  multi_az = var.db_multi_az

  backup_retention_period = 7

  backup_window      = "17:00-18:00"
  maintenance_window = "sun:18:00-sun:19:00"

  auto_minor_version_upgrade = true

  deletion_protection = true

  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-${var.environment}-postgres-final"

  copy_tags_to_snapshot = true

  enabled_cloudwatch_logs_exports = [
    "postgresql",
    "upgrade"
  ]

  apply_immediately = false

  tags = {
    Name = "${var.project_name}-${var.environment}-postgres"
  }
}

############ RDS Access ############

resource "aws_vpc_security_group_ingress_rule" "rds_from_app_ec2" {
  security_group_id = aws_security_group.rds.id

  referenced_security_group_id = aws_security_group.app_ec2.id

  description = "Allow PostgreSQL from application Docker host"

  ip_protocol = "tcp"
  from_port   = 5432
  to_port     = 5432
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_msk_connect" {
  security_group_id = aws_security_group.rds.id

  referenced_security_group_id = aws_security_group.msk_client.id

  description = "Allow PostgreSQL from MSK Connect"

  ip_protocol = "tcp"
  from_port   = 5432
  to_port     = 5432
}