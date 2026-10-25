# Both application containers use the instance profile for IAM authentication.
# With one EC2 host, these permissions are shared by both services.
locals {
  audit_source_topics = [
    "transport.public.transport",
    "transport.public.job",
    "transport.public.container"
  ]

  audit_dlt_topics = [for topic in local.audit_source_topics : "${topic}.dlt"]
  audit_all_topics = concat(local.audit_source_topics, local.audit_dlt_topics)
}

data "aws_iam_policy_document" "app_access" {
  statement {
    sid     = "ReadApplicationDatabaseSecrets"
    effect  = "Allow"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      aws_secretsmanager_secret.transport_db_app.arn,
      aws_secretsmanager_secret.audit_db_app.arn,
      aws_secretsmanager_secret.debezium_db.arn,
      aws_db_instance.postgres.master_user_secret[0].secret_arn
    ]
  }

  statement {
    sid     = "ConnectToMSK"
    effect  = "Allow"
    actions = ["kafka-cluster:Connect", "kafka-cluster:DescribeCluster", "kafka-cluster:WriteDataIdempotently"]
    resources = [aws_msk_serverless_cluster.main.arn]
  }

  statement {
    sid     = "ReadKafkaTopics"
    effect  = "Allow"
    actions = ["kafka-cluster:DescribeTopic", "kafka-cluster:ReadData"]
    resources = [for topic in local.audit_all_topics : "${local.msk_topic_arn_prefix}/${topic}"]
  }

  statement {
    sid     = "CreateAndWriteDeadLetterTopics"
    effect  = "Allow"
    actions = ["kafka-cluster:CreateTopic", "kafka-cluster:DescribeTopic", "kafka-cluster:WriteData"]
    resources = [for topic in local.audit_dlt_topics : "${local.msk_topic_arn_prefix}/${topic}"]
  }

  statement {
    sid     = "KafkaConsumerGroups"
    effect  = "Allow"
    actions = ["kafka-cluster:DescribeGroup", "kafka-cluster:AlterGroup"]
    resources = [
      "${local.msk_group_arn_prefix}/audit-consumer",
      "${local.msk_group_arn_prefix}/audit-dlt-replay"
    ]
  }
}

resource "aws_iam_role_policy" "app_access" {
  name   = "production-app-access"
  role   = module.app_ec2.role_name
  policy = data.aws_iam_policy_document.app_access.json
}
