############ Plugin ############

locals {
  debezium_version = "3.7.0.Final"

  msk_connect_plugin_bucket_name = "${var.project_name}-${var.aws_account_id}-${var.aws_region}-msk-connect-plugins"

  msk_connect_plugin_key = "debezium-postgres/${local.debezium_version}/plugin.zip"

  msk_topic_arn_prefix = replace(
    aws_msk_serverless_cluster.main.arn,
    ":cluster/",
    ":topic/"
  )

  msk_group_arn_prefix = replace(
    aws_msk_serverless_cluster.main.arn,
    ":cluster/",
    ":group/"
  )
}

data "aws_s3_bucket" "msk_connect_plugins" {
  count  = var.enable_msk_connect ? 1 : 0
  bucket = local.msk_connect_plugin_bucket_name
}

data "aws_s3_object" "debezium_plugin" {
  count  = var.enable_msk_connect ? 1 : 0
  bucket = local.msk_connect_plugin_bucket_name
  key    = local.msk_connect_plugin_key
}

resource "aws_mskconnect_custom_plugin" "debezium_postgres" {
  count = var.enable_msk_connect ? 1 : 0
  name  = "${var.project_name}-${var.environment}-debezium-postgres-${replace(local.debezium_version, ".", "-")}"

  content_type = "ZIP"

  location {
    s3 {
      bucket_arn     = data.aws_s3_bucket.msk_connect_plugins[0].arn
      file_key       = local.msk_connect_plugin_key
      object_version = data.aws_s3_object.debezium_plugin[0].version_id
    }
  }
}

############ Worker Configuration ############

resource "aws_mskconnect_worker_configuration" "debezium" {
  name = "${var.project_name}-${var.environment}-debezium"

  properties_file_content = <<-PROPERTIES
    key.converter=org.apache.kafka.connect.json.JsonConverter
    key.converter.schemas.enable=false

    value.converter=org.apache.kafka.connect.json.JsonConverter
    value.converter.schemas.enable=false

    config.providers=secretsmanager
    config.providers.secretsmanager.class=com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider
    config.providers.secretsmanager.param.region=${var.aws_region}
  PROPERTIES
}

############ CloudWatch ############

resource "aws_cloudwatch_log_group" "msk_connect" {
  name = "/aws/msk-connect/${var.project_name}/${var.environment}/debezium"

  retention_in_days = 14

  tags = {
    Name = "${var.project_name}-${var.environment}-debezium"
  }
}

############ IAM Role ############

data "aws_iam_policy_document" "msk_connect_assume_role" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRole"
    ]

    principals {
      type = "Service"

      identifiers = [
        "kafkaconnect.amazonaws.com"
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"

      values = [
        var.aws_account_id
      ]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"

      values = [
        "arn:${data.aws_partition.current.partition}:kafkaconnect:${var.aws_region}:${var.aws_account_id}:connector/*"
      ]
    }
  }
}

resource "aws_iam_role" "msk_connect" {
  name = "${var.project_name}-${var.environment}-msk-connect"

  assume_role_policy = data.aws_iam_policy_document.msk_connect_assume_role.json

  tags = {
    Name = "${var.project_name}-${var.environment}-msk-connect"
  }
}

data "aws_iam_policy_document" "msk_connect" {
  statement {
    sid    = "ConnectToMSK"
    effect = "Allow"

    actions = [
      "kafka-cluster:Connect",
      "kafka-cluster:DescribeCluster"
    ]

    resources = [
      aws_msk_serverless_cluster.main.arn
    ]
  }

  statement {
    sid    = "KafkaTopics"
    effect = "Allow"

    actions = [
      "kafka-cluster:CreateTopic",
      "kafka-cluster:DescribeTopic",
      "kafka-cluster:ReadData",
      "kafka-cluster:WriteData"
    ]

    resources = [
      "${local.msk_topic_arn_prefix}/*"
    ]
  }

  statement {
    sid    = "KafkaGroups"
    effect = "Allow"

    actions = [
      "kafka-cluster:DescribeGroup",
      "kafka-cluster:AlterGroup"
    ]

    resources = [
      "${local.msk_group_arn_prefix}/*"
    ]
  }

  statement {
    sid    = "ReadDatabaseSecret"
    effect = "Allow"

    actions = [
      "secretsmanager:GetSecretValue"
    ]

    resources = [
      aws_secretsmanager_secret.debezium_db.arn
    ]
  }

  statement {
    sid    = "WriteLogs"
    effect = "Allow"

    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]

    resources = [
      "${aws_cloudwatch_log_group.msk_connect.arn}:*"
    ]
  }
}

resource "aws_iam_role_policy" "msk_connect" {
  name = "msk-connect"

  role = aws_iam_role.msk_connect.id

  policy = data.aws_iam_policy_document.msk_connect.json
}

############ Debezium Connector ############

resource "aws_mskconnect_connector" "transport_cdc" {
  count = var.enable_msk_connect ? 1 : 0

  name = "${var.project_name}-${var.environment}-transport-cdc"

  kafkaconnect_version = "3.7.x"

  capacity {
    provisioned_capacity {
      mcu_count    = 1
      worker_count = 1
    }
  }

  connector_configuration = {
    "connector.class" = "io.debezium.connector.postgresql.PostgresConnector"
    "tasks.max"       = "1"

    "database.hostname" = aws_db_instance.postgres.address
    "database.port"     = tostring(aws_db_instance.postgres.port)
    "database.dbname"   = var.db_name

    "database.user" = "$${secretsmanager:${aws_secretsmanager_secret.debezium_db.name}:username}"

    "database.password" = "$${secretsmanager:${aws_secretsmanager_secret.debezium_db.name}:password}"

    "database.sslmode" = "require"

    "plugin.name" = "pgoutput"

    "slot.name" = "transport_cdc_slot"

    "publication.name"            = "transport_cdc_publication"
    "publication.autocreate.mode" = "disabled"

    "topic.prefix" = "transport"

    "table.include.list" = "public.transport,public.job,public.container"

    "snapshot.mode" = "initial"

    "provide.transaction.metadata" = "true"

    "topic.creation.default.partitions"         = "3"
    "topic.creation.default.replication.factor" = "-1"
  }

  kafka_cluster {
    apache_kafka_cluster {
      bootstrap_servers = aws_msk_serverless_cluster.main.bootstrap_brokers_sasl_iam

      vpc {
        subnets = aws_subnet.app[*].id

        security_groups = [
          aws_security_group.msk_client.id
        ]
      }
    }
  }

  kafka_cluster_client_authentication {
    authentication_type = "IAM"
  }

  kafka_cluster_encryption_in_transit {
    encryption_type = "TLS"
  }

  plugin {
    custom_plugin {
      arn      = aws_mskconnect_custom_plugin.debezium_postgres[0].arn
      revision = aws_mskconnect_custom_plugin.debezium_postgres[0].latest_revision
    }
  }

  worker_configuration {
    arn      = aws_mskconnect_worker_configuration.debezium.arn
    revision = aws_mskconnect_worker_configuration.debezium.latest_revision
  }

  # NAT is provisioned when enabling MSK Connect; ensure the private route exists first.
  depends_on = [aws_route.app_internet]

  service_execution_role_arn = aws_iam_role.msk_connect.arn

  log_delivery {
    worker_log_delivery {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk_connect.name
      }
    }
  }

  tags = {
    Name = "${var.project_name}-${var.environment}-transport-cdc"
  }
}