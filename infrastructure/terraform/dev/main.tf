resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr

  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.project_name}-${var.environment}-vpc"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-igw"
  }
}

resource "aws_subnet" "public" {
  vpc_id = aws_vpc.main.id

  # add 8 network bits
  cidr_block = cidrsubnet(var.vpc_cidr, 8, 0)

  # Automatically assigns public IPv4 addresses to supported instances launched in this subnet
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-${var.environment}-public"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-public-rt"
  }
}

resource "aws_route" "internet" {
  route_table_id = aws_route_table.public.id

  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

# associates your public subnet with the public route table
resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "dev" {
  name        = "${var.project_name}-${var.environment}"
  description = "Security group for cdc-kafka development EC2"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-${var.environment}-sg"
  }
}

resource "aws_vpc_security_group_egress_rule" "dev_outbound" {
  security_group_id = aws_security_group.dev.id

  # all protocols
  ip_protocol = "-1"
  cidr_ipv4   = "0.0.0.0/0"

  description = "Allow development EC2 outbound traffic"
}