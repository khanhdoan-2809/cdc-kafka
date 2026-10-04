project_name = "cdc-kafka"
environment  = "prod"
aws_region   = "ap-southeast-1"

aws_account_id = "123456789012" # replace real AWS account ID
vpc_cidr = "10.20.0.0/16"

db_name            = "transport"
db_master_username = "cdc_admin"
db_instance_class = "db.t4g.micro"
db_allocated_storage     = 20
db_max_allocated_storage = 100
db_multi_az = false

transport_container_port = 8080
transport_task_cpu    = 512
transport_task_memory = 1024
# Keep zero until DB bootstrap + CD are ready.
transport_desired_count = 0