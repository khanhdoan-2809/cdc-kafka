resource "aws_db_subnet_group" "main" {
  name = "${var.project_name}-${var.environment}"

  subnet_ids = aws_subnet.database[*].id

  tags = {
    Name = "${var.project_name}-${var.environment}-db-subnet-group"
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-${var.environment}-rds"
  description = "Security group for PostgreSQL RDS"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-rds-sg"
  }
}

resource "aws_db_instance" "postgres" {
  identifier = "${var.project_name}-${var.environment}-postgres"

  engine         = "postgres"
  engine_version = "17.11"

  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_master_username

  # RDS generates the administrator password and stores/manages it through Secrets Manager
  manage_master_user_password = true

  port = 5432

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage

  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name = aws_db_subnet_group.main.name

  vpc_security_group_ids = [
    aws_security_group.rds.id
  ]

  # RDS is private
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