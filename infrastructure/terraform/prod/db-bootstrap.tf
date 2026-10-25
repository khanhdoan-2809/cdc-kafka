############ Application Database Secrets ############

resource "aws_secretsmanager_secret" "transport_db_app" {
  name = "${var.project_name}/${var.environment}/database/transport-app"

  recovery_window_in_days = 7

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-db-app"
  }
}

resource "aws_secretsmanager_secret" "audit_db_app" {
  name = "${var.project_name}/${var.environment}/database/audit-app"

  recovery_window_in_days = 7

  tags = {
    Name = "${var.project_name}-${var.environment}-audit-db-app"
  }
}

resource "aws_secretsmanager_secret" "debezium_db" {
  name = "${var.project_name}/${var.environment}/database/debezium"

  recovery_window_in_days = 7

  tags = {
    Name = "${var.project_name}-${var.environment}-debezium-db"
  }
}
